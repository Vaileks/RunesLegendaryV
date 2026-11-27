"""Verify the eight exported meshes and the original embedded Blockbench texture."""
from pathlib import Path
import base64,json,struct,sys,zipfile
PARTS=('vine','vine_1','vine_2','vine_3','vine2','vine2_1','vine2_2','vine2_3')
with zipfile.ZipFile(sys.argv[1]) as pack:
 assert pack.testzip() is None
 assert len(pack.namelist())==17
 texture=pack.read('assets/runes/textures/item/effects/life_vines/vine_root.png')
 assert texture[:8]==b'\x89PNG\r\n\x1a\n' and struct.unpack('>II',texture[16:24])==(64,64)
 if len(sys.argv)>2:
  source=json.loads(Path(sys.argv[2]).read_text());assert texture==base64.b64decode(source['textures'][0]['source'].split(',',1)[1])
 total=0
 for index,name in enumerate(PARTS):
  item=json.loads(pack.read(f'assets/runes/items/effects/life_vines/{name}.json'))
  assert item['model']['model']=='runes:effects/life_vines/'+name
  assert item['model']['tints']==[{'type':'minecraft:constant','value':16777215}]
  model=json.loads(pack.read(f'assets/runes/models/effects/life_vines/{name}.json'))
  assert model['textures']['0']=='runes:item/effects/life_vines/vine_root'
  assert len(model['elements'])==(1 if index%4==3 else 7)
  total+=len(model['elements'])
  stem=model['elements'][0];assert abs(stem['from'][1]-8)<1e-5 and abs(stem['to'][1]-17)<1e-5
  assert abs((stem['to'][0]-stem['from'][0])-(5-index%4))<1e-5
  for cube in model['elements']:
   assert all(-16<=v<=32 for field in ('from','to') for v in cube[field])
   for face in cube['faces'].values():
    assert face['texture']=='#0' and all(0<=v<=16 for v in face['uv'])
    assert face.get('rotation',0) in (0,90,180,270)
   if 'rotation' in cube:assert cube['rotation']['angle'] in (-45,-22.5,0,22.5,45)
 assert total==44
print('PASS: eight complete meshes, all 44 original cubes, original texture, valid model/texture references and UV rotations')
