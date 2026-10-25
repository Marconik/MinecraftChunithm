"""Export the verified C2S subset to the MOD's visual playback schema."""
import argparse
import json
from pathlib import Path
from fractions import Fraction
from song_catalog import update_song_index
from speed_change_markers import build_speed_change_markers

AIR_TYPES = {'AIR','AUL','AUR','ADW','ADL','ADR'}
SLIDE_TYPES = {'SLD','SLC','SXD','SXC'}
AIR_PATH_TYPES = {'ASC','ASD'}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('song_ids',type=int,nargs='+')
    parser.add_argument('--difficulties', nargs='+', choices=['MASTER','ULTIMA'], default=['MASTER'])
    args=parser.parse_args()
    project=Path(__file__).resolve().parents[1]
    for song_id, difficulty in ((song_id,difficulty) for song_id in args.song_ids for difficulty in args.difficulties):
        folder=f'{song_id:04d}'
        suffix={'MASTER':'03','ULTIMA':'04'}[difficulty]
        analysis=json.loads((project/f'build/chart-analysis/{folder}_{suffix}.analysis.json').read_text(encoding='utf-8'))
        if analysis['unclassifiedRecords'] or not all(analysis['validation'].values()):
            raise ValueError('Unverified chart subset')
        identity=analysis['identity']
        if identity['musicId']!=song_id or identity['difficulty'].upper()!=difficulty:
            raise ValueError('Chart identity mismatch')
        notes=[]
        for row in analysis['records']:
            note={key:row[key] for key in ('id','command','timeMs','lane','width')}
            note['sourceOrder']=row['line']
            note['sourceFields']=row['fields']
            note['scrollProfile']=analysis.get('scrollAssignments',{}).get(row['id'],0)
            if 'endTimeMs' in row:
                note.update(endTimeMs=row['endTimeMs'],endLane=row['endLane'],endWidth=row['endWidth'])
            if row['command'] in AIR_TYPES | AIR_PATH_TYPES | {'AHD','AHX'}:
                note['targetType']=row['fields'][4]
            if row['command'] in ('CHR','FLK'):
                note['sourceDirection']=row['fields'][4]
            if row['command']=='AHX':
                note['color']=row['fields'][6] if len(row['fields'])>6 else 'DEF'
            if row['command']=='HXD':
                note['animation']=row['fields'][5]
            if row['command'] in SLIDE_TYPES:
                note['root']=not row['predecessors']
                note['terminal']=not row['successors']
                if len(row['fields'])>=8: note['targetType']=row['fields'][7]
                if len(row['fields'])==9: note['animation']=row['fields'][8]
            if row['command'] in AIR_PATH_TYPES | {'AHD','AHX'}:
                note.update(root=row['root'],terminal=row['terminal'])
            for key in ('height','endHeight','color','crushTick'):
                if key in row: note[key]=row[key]
            if row['command'] in AIR_TYPES | {'AHD','AHX'} and row['fields'][-1] == 'DEF':
                note['color']='DEF'
            # Preserve source coordinates before making a bounded playback copy.
            # Associations and scroll profiles were resolved against the original path.
            outside = row['lane'] < 0 or row['lane'] + row['width'] > 16
            outside |= row.get('endLane', row['lane']) < 0 or row.get('endLane', row['lane']) + row.get('endWidth', row['width']) > 16
            if outside:
                note['sourceOutOfBounds'] = True
                note['sourceLane'] = row['lane']
                note['lane'] = max(0, min(16 - row['width'], row['lane']))
                if 'endLane' in row:
                    note['sourceEndLane'] = row['endLane']
                    note['endLane'] = max(0, min(16 - row['endWidth'], row['endLane']))
            notes.append(note)
        tempos=sorted(analysis['tempoEvents'],key=lambda event:event['tick'])
        beats=[]
        for tick in range(0,analysis['timing']['lastTick']+1,96):
            tempo=max((event for event in tempos if event['tick']<=tick),key=lambda event:event['tick'])
            value=Fraction(str(tempo['timeMs']))+Fraction((tick-tempo['tick'])*60000,96)/Fraction(tempo['fields'][2])
            beats.append(round(float(value),6))
        result={'schemaVersion':1,'songId':song_id,'title':identity['title'],'difficulty':difficulty,
                'lastTimeMs':analysis['timing'].get('visualLastTimeMs',analysis['timing']['lastTimeMs']),'notes':notes,
                'beatTimesMs':beats,
                'scrollEvents':analysis['scrollEvents'],'speedChangeMarkers':analysis['speedChangeMarkers'],
                'tempoEvents':analysis['tempoEvents'],'meterEvents':analysis['meterEvents'],
                'clockEvents':analysis.get('clockEvents',[]),
                'sourceVisualControls':analysis.get('sourceVisualControls',[]),
                'visualWarnings':analysis.get('visualWarnings',[]),
                'sourceHeader':analysis['header'],'sourceStatistics':analysis['sourceStatistics']}
        outside_count = sum(note.get('sourceOutOfBounds', False) for note in notes)
        result['outOfBoundsPolicy'] = dict(mode='CLAMP_DISPLAY_PRESERVE_SOURCE', recordCount=outside_count)
        if outside_count:
            result['visualWarnings'] = [*result['visualWarnings'],
                f'{outside_count} out-of-bounds source records retained; off-track flight is deferred and playback coordinates are clamped.']
        if 'resolvedScrollEvents' in analysis:
            result['sourceScrollEvents']=analysis['scrollEvents']
            result['scrollEvents']=analysis['resolvedScrollEvents']
            result['scrollProfiles']=analysis['scrollProfiles']
            result['scrollProfileResolution']=analysis['scrollProfileResolution']
            # Profile markers are not new note judgments. Default-curve changes
            # retain the existing full-width visual boundary convention.
            time_by_tick={e[k]:e[v] for e in result['scrollEvents'] for k,v in [('tick','timeMs'),('endTick','endTimeMs')]}
            marker_events=[dict(e,line=i,id=f'profile0_{i}') for i,e in enumerate(result['scrollEvents'])]
            result['speedChangeMarkers']=build_speed_change_markers(marker_events,time_by_tick.__getitem__)
        resource=f'songs/{folder}/{difficulty.lower()}.json'
        target=project/'src/main/resources/data/minecraftchunithm'/resource
        target.parent.mkdir(parents=True,exist_ok=True)
        target.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        metadata_path=project/f'src/main/resources/data/minecraftchunithm/songs/{folder}/song.json'
        metadata=json.loads(metadata_path.read_text(encoding='utf-8'))
        metadata['charts'][difficulty].update(chartResource=f'minecraftchunithm:{resource}',imported=True,
                visualWarnings=result['visualWarnings'],speedChangeMarkerCount=len(result['speedChangeMarkers']))
        metadata_path.write_text(json.dumps(metadata,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        print(f'Exported {song_id} {difficulty}: {len(notes)} records, {len(result["speedChangeMarkers"])} speed markers')
    update_song_index(project/'src/main/resources')


if __name__=='__main__': main()
