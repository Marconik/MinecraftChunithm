"""Export the verified C2S subset to the MOD's visual playback schema."""
import argparse
import json
from pathlib import Path
from fractions import Fraction
from song_catalog import update_song_index

AIR_TYPES = {'AIR','AUL','AUR','ADW','ADL','ADR'}
SLIDE_TYPES = {'SLD','SLC','SXD','SXC'}
AIR_PATH_TYPES = {'ASC','ASD'}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('song_ids',type=int,nargs='+')
    args=parser.parse_args()
    project=Path(__file__).resolve().parents[1]
    for song_id in args.song_ids:
        folder=f'{song_id:04d}'
        analysis=json.loads((project/f'build/chart-analysis/{folder}_03.analysis.json').read_text(encoding='utf-8'))
        if analysis['unclassifiedRecords'] or not all(analysis['validation'].values()):
            raise ValueError('Unverified chart subset')
        identity=analysis['identity']
        if identity['musicId']!=song_id or identity['difficulty']!='Master':
            raise ValueError('Chart identity mismatch')
        notes=[]
        for row in analysis['records']:
            note={key:row[key] for key in ('id','command','timeMs','lane','width')}
            note['sourceOrder']=row['line']
            note['sourceFields']=row['fields']
            if 'endTimeMs' in row:
                note.update(endTimeMs=row['endTimeMs'],endLane=row['endLane'],endWidth=row['endWidth'])
            if row['command'] in AIR_TYPES | AIR_PATH_TYPES | {'AHD'}:
                note['targetType']=row['fields'][4]
            if row['command'] in ('CHR','FLK'):
                note['sourceDirection']=row['fields'][4]
            if row['command']=='HXD':
                note['animation']=row['fields'][5]
            if row['command'] in SLIDE_TYPES:
                note['root']=not row['predecessors']
                note['terminal']=not row['successors']
                if len(row['fields'])>=8: note['targetType']=row['fields'][7]
                if len(row['fields'])==9: note['animation']=row['fields'][8]
            if row['command'] in AIR_PATH_TYPES | {'AHD'}:
                note.update(root=row['root'],terminal=row['terminal'])
            for key in ('height','endHeight','color','crushTick'):
                if key in row: note[key]=row[key]
            if row['command'] in AIR_TYPES | {'AHD'} and row['fields'][-1] == 'DEF':
                note['color']='DEF'
            notes.append(note)
        tempos=sorted(analysis['tempoEvents'],key=lambda event:event['tick'])
        beats=[]
        for tick in range(0,analysis['timing']['lastTick']+1,96):
            tempo=max((event for event in tempos if event['tick']<=tick),key=lambda event:event['tick'])
            value=Fraction(str(tempo['timeMs']))+Fraction((tick-tempo['tick'])*60000,96)/Fraction(tempo['fields'][2])
            beats.append(round(float(value),6))
        result={'schemaVersion':1,'songId':song_id,'title':identity['title'],'difficulty':'MASTER',
                'lastTimeMs':analysis['timing'].get('visualLastTimeMs',analysis['timing']['lastTimeMs']),'notes':notes,
                'beatTimesMs':beats,
                'scrollEvents':analysis['scrollEvents'],'speedChangeMarkers':analysis['speedChangeMarkers'],
                'tempoEvents':analysis['tempoEvents'],'meterEvents':analysis['meterEvents'],
                'clockEvents':analysis.get('clockEvents',[]),
                'sourceHeader':analysis['header'],'sourceStatistics':analysis['sourceStatistics']}
        resource=f'songs/{folder}/master.json'
        target=project/'src/main/resources/data/minecraftchunithm'/resource
        target.parent.mkdir(parents=True,exist_ok=True)
        target.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        metadata_path=project/f'src/main/resources/data/minecraftchunithm/songs/{folder}/song.json'
        metadata=json.loads(metadata_path.read_text(encoding='utf-8'))
        metadata['charts']['MASTER'].update(chartResource=f'minecraftchunithm:{resource}',imported=True)
        metadata_path.write_text(json.dumps(metadata,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        print(f'Exported {song_id}: {len(notes)} records, {len(result["speedChangeMarkers"])} speed markers')
    update_song_index(project/'src/main/resources')


if __name__=='__main__': main()
