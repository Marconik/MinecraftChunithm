"""Verify profile overrides, SLA edges, attachment continuity and conflict rejection."""
from scroll_profiles import compile_profiles


def curve(profile, a, b, multiplier, line=1):
    return dict(command='SLP', profileId=profile, tick=a, endTick=b, multiplier=multiplier, line=line)


def area(a, b, lane=6, width=4, profile=1):
    return dict(command='SLA', profileId=profile, tick=a, endTick=b, lane=lane, width=width, line=10)


def note(key, tick, lane=6, width=4, **extra):
    return dict(id=key, tick=tick, lane=lane, width=width, **extra)


def main():
    controls=[curve(0,0,1000,.5),curve(1,50,500,.4),curve(1,200,300,.25,2),area(100,101)]
    records=[note('tap',100),note('air',100,anchorCandidates=[dict(id='tap')]),note('outside_time',101),
             note('outside_lane',100,0,2),note('partial',100,5,4),note('segment',200,predecessors=['tap'])]
    result=compile_profiles(controls,records,[],float)
    assert result['scrollAssignments']=={'tap':1,'air':1,'segment':1}
    events=result['scrollProfiles'][0]['events']
    assert [(e['tick'],e['endTick'],e['multiplier']) for e in events]==[
        (0,50,.5),(50,200,.4),(200,300,.25),(300,500,.4),(500,1000,.5)]
    assert all(e['timeMs']==e['tick'] and e['endTimeMs']==e['endTick'] for e in events)
    for invalid in [controls+[area(100,101,profile=2)],
                    controls+[curve(2,0,1000,.3),area(100,101,profile=2)],
                    controls+[curve(2,0,1000,.3),area(200,201,profile=2)]]:
        try:
            compile_profiles(invalid,records,[],float)
        except ValueError:
            pass
        else:
            raise AssertionError('Undefined, overlapping or disconnected-path profile conflict accepted')
    assert compile_profiles([],records,[],float)['scrollAssignments']=={}
    print('Scroll profiles: default inheritance, local overrides, SLA half-open bounds/lane containment, Air/path continuity and conflicting IDs passed.')


if __name__=='__main__': main()
