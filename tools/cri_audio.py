"""Read CRI UTF tables and AFS2 banks without modifying the source containers.

Cue traversal follows the table relationships documented by vgmstream's acb.c:
https://github.com/vgmstream/vgmstream/blob/master/src/meta/acb.c
This exposes source waveforms; it does not emulate the Atom mixing engine.
"""
from functools import cache
import struct


def read_utf(data):
    if data[:4] != b'@UTF' or len(data) < 32:
        raise ValueError('Expected a CRI UTF table')
    size, version, rows_at, strings_at, binary_at, name_at, columns, row_size, count = struct.unpack_from('>IHHIIIHHI', data, 4)
    if size + 8 > len(data) or count > 2_000_000:
        raise ValueError('Truncated or oversized UTF table')
    strings_at += 8
    binary_at += 8
    rows_at += 8

    def string(offset):
        start = strings_at + offset
        if not strings_at <= start < len(data):
            raise ValueError('Invalid UTF string offset')
        return data[start:data.index(b'\0', start)].decode('utf-8')

    formats = {0:'>B', 1:'>b', 2:'>H', 3:'>h', 4:'>I', 5:'>i',
               6:'>Q', 7:'>q', 8:'>f', 9:'>d', 10:'>I', 11:'>II'}

    def value(at, kind):
        result = struct.unpack_from(formats[kind], data, at)
        decoded = result[0]
        if kind == 10:
            decoded = string(decoded)
        elif kind == 11:
            start, length = binary_at + result[0], result[1]
            if not binary_at <= start <= start + length <= len(data):
                raise ValueError('Invalid UTF binary bounds')
            decoded = data[start:start + length]
        return decoded, struct.calcsize(formats[kind])

    definitions, cursor = [], 32
    for _ in range(columns):
        flag, offset = struct.unpack_from('>BI', data, cursor)
        cursor += 5
        storage, kind = flag & 0xf0, flag & 15
        if storage not in (0x10, 0x30, 0x50) or kind not in formats:
            raise ValueError(f'Unsupported UTF column flag: {flag:#x}')
        constant = {10:'', 11:b''}.get(kind, 0)
        if storage == 0x30:
            constant, length = value(cursor, kind)
            cursor += length
        definitions.append((string(offset), storage, kind, constant))
    rows = []
    for index in range(count):
        cursor, row = rows_at + index * row_size, {}
        if cursor + row_size > strings_at:
            raise ValueError('Invalid UTF row bounds')
        for name, storage, kind, constant in definitions:
            current = constant
            if storage == 0x50:
                current, length = value(cursor, kind)
                cursor += length
            row[name] = current
        rows.append(row)
    return string(name_at), rows


def read_afs2(data):
    if len(data) < 16 or data[:4] != b'AFS2':
        raise ValueError('Expected an AFS2 bank')
    offset_width, id_width = data[5], int.from_bytes(data[6:8], 'little')
    count, alignment, subkey = struct.unpack_from('<IHH', data, 8)
    if offset_width not in (2,4,8) or id_width not in (1,2,4) or not alignment:
        raise ValueError('Unsupported AFS2 layout')
    offset_at = 16 + count * id_width
    table_end = offset_at + (count + 1) * offset_width
    if table_end > len(data):
        raise ValueError('Truncated AFS2 table')
    offsets = [int.from_bytes(data[offset_at+i*offset_width:offset_at+(i+1)*offset_width], 'little') for i in range(count+1)]
    streams = {}
    for i in range(count):
        identifier = int.from_bytes(data[16+i*id_width:16+(i+1)*id_width], 'little')
        start = (offsets[i] + alignment - 1) // alignment * alignment
        end = offsets[i+1]
        if identifier in streams or not table_end <= start < end <= len(data):
            raise ValueError('Invalid AFS2 stream bounds or duplicate ID')
        streams[identifier] = data[start:end]
    return streams, subkey


class CueSheet:
    def __init__(self, data):
        name, rows = read_utf(data)
        if name != 'Header' or len(rows) != 1:
            raise ValueError('Expected a single-row ACB Header')
        self.header = rows[0]

    @cache
    def table(self, name):
        return read_utf(self.header[name])[1]

    def cues(self):
        cues = self.table('CueTable')
        return [dict(name=row['CueName'], index=row['CueIndex'], **cues[row['CueIndex']])
                for row in self.table('CueNameTable')]

    def waveforms(self, cue):
        """Return every source variant/layer with its exact ACB traversal path."""
        leaves = []

        def walk(kind, index, path, active):
            node = (kind, index)
            if node in active or len(active) >= 16:
                raise ValueError(f'Circular/oversized cue graph: {path}')
            active = active | {node}
            path = path + [f'{kind}[{index}]']
            if kind == 'Cue':
                row = self.table('CueTable')[index]
                follow(row['ReferenceType'], row['ReferenceIndex'], path, active)
            elif kind == 'Waveform':
                leaves.append(dict(index=index, waveform=self.table('WaveformTable')[index], path=path))
            elif kind == 'Synth':
                refs = self.table('SynthTable')[index]['ReferenceItems']
                if len(refs) % 4:
                    raise ValueError('Invalid Synth reference list')
                for at in range(0, len(refs), 4):
                    reference, target = struct.unpack_from('>HH', refs, at)
                    if reference == 0:
                        break
                    follow(reference, target, path, active)
            elif kind == 'Sequence':
                row = self.table('SequenceTable')[index]
                refs, count = row['TrackIndex'], row['NumTracks']
                if len(refs) < count * 2:
                    raise ValueError('Truncated Sequence track list')
                for at in range(count):
                    target = struct.unpack_from('>H', refs, at*2)[0]
                    if target != 65535:
                        walk('Track', target, path, active)
                # Some TAP cues contain only actions that start other local
                # cues (e.g. a selected hit-sound bank plus the loop layer).
                start, count = row.get('ActionTrackStartIndex', 65535), row.get('NumActionTracks', 0)
                if count:
                    if start == 65535:
                        raise ValueError('Missing action-track start')
                    for target in range(start, start + count):
                        walk('Action', target, path, active)
            elif kind == 'Action':
                row = self.table('ActionTrackTable')[index]
                commands = self.table('TrackEventTable')[row['CommandIndex']]['Command']
                at, starts = 0, False
                while at < len(commands):
                    code, length = struct.unpack_from('>HB', commands, at)
                    at += 3
                    if at + length > len(commands):
                        raise ValueError('Truncated action command')
                    starts |= code == 7100
                    at += length
                if starts:
                    if row['TargetType'] != 1 or row['TargetAcbName'] not in ('', self.header['Name']):
                        raise ValueError('Unsupported cross-sheet/non-cue action')
                    targets = [(i, c) for i, c in enumerate(self.table('CueTable')) if c['CueId'] == row['TargetId']]
                    if len(targets) != 1:
                        raise ValueError('Ambiguous action Cue ID')
                    walk('Cue', targets[0][0], path, active)
            elif kind == 'Track':
                event = self.table('TrackTable')[index]['EventIndex']
                if event == 65535:
                    return
                table = 'TrackEventTable' if self.header.get('TrackEventTable') else 'CommandTable'
                commands = self.table(table)[event]['Command']
                at = 0
                while at < len(commands):
                    code, length = struct.unpack_from('>HB', commands, at)
                    at += 3
                    if at + length > len(commands):
                        raise ValueError('Truncated track command')
                    if code in (2000, 2003):
                        if length < 4:
                            raise ValueError('Truncated note-on reference')
                        reference, target = struct.unpack_from('>HH', commands, at)
                        follow(reference, target, path + [f'{table}[{event}]/noteOn'], active)
                    elif code in (2002, 2004):
                        raise ValueError(f'Unimplemented waveform command {code}')
                    at += length
            else:
                raise ValueError(f'Unsupported reference: {kind}')

        def follow(reference, target, path, active):
            # Local insideLinkSequence references reuse the target sequence;
            # this bank uses them for the Critical/J-Critical sound-set aliases.
            kinds = {1:'Waveform', 2:'Synth', 3:'Sequence', 7:'Sequence'}
            if reference not in kinds:
                raise ValueError(f'Unsupported ACB reference type {reference}, target={target}, path={path}')
            walk(kinds[reference], target, path, active)

        walk('Cue', cue['index'], [], set())
        if not leaves:
            raise ValueError(f"No waveforms for {cue['name']}")
        return leaves
