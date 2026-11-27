"""Export a supplied Blockbench vine mesh for the existing eight-part ItemDisplay rig.

Only mesh-local geometry is exported. Growth and wrapping stay in PlantVineRig.
No ModelEngine, BetterModel or MythicMobs plugin is needed.
"""
from pathlib import Path
import base64,copy,itertools,json,math,sys,zipfile

PARTS = ('vine','vine_1','vine_2','vine_3','vine2','vine2_1','vine2_2','vine2_3')
STEMS = ('liana2','group10','group11','group12','liana3','group28','group29','group30')
SIDES = {'north':(0,0,-1),'south':(0,0,1),'east':(1,0,0),'west':(-1,0,0),'up':(0,1,0),'down':(0,-1,0)}

def add(a,b): return [x+y for x,y in zip(a,b)]
def sub(a,b): return [x-y for x,y in zip(a,b)]
def mul(m,v): return [sum(a*b for a,b in zip(row,v)) for row in m]
def mm(a,b): return [[sum(a[i][k]*b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]
def rotation(degrees):
    x,y,z=map(math.radians,degrees);cx,sx=math.cos(x),math.sin(x);cy,sy=math.cos(y),math.sin(y);cz,sz=math.cos(z),math.sin(z)
    return mm(mm([[cz,-sz,0],[sz,cz,0],[0,0,1]],[[cy,0,sy],[0,1,0],[-sy,0,cy]]),[[1,0,0],[0,cx,-sx],[0,sx,cx]])
def cardinal(degrees):
    matrix=rotation(degrees)
    rounded=[[round(v) for v in row] for row in matrix]
    if any(abs(matrix[i][j]-rounded[i][j])>1e-6 for i in range(3) for j in range(3)):
        raise ValueError('Non-cardinal child-group rotation cannot be represented without changing the mesh')
    return rounded

def face_vertices(lo,hi,side):
    x,y,z=lo;X,Y,Z=hi
    return {'north':[(X,Y,z),(x,Y,z),(x,y,z),(X,y,z)],'south':[(x,Y,Z),(X,Y,Z),(X,y,Z),(x,y,Z)],
            'east':[(X,Y,Z),(X,Y,z),(X,y,z),(X,y,Z)],'west':[(x,Y,z),(x,Y,Z),(x,y,Z),(x,y,z)],
            'up':[(x,Y,z),(X,Y,z),(X,Y,Z),(x,Y,Z)],'down':[(x,y,Z),(X,y,Z),(X,y,z),(x,y,z)]}[side]

def face_uv(uv,angle=0):
    u,v,U,V=uv;corners=[(u,v),(U,v),(U,V),(u,V)]
    shift=(angle//90)%4
    return corners[-shift:]+corners[:-shift] if shift else corners

def near(a,b): return all(abs(x-y)<1e-5 for x,y in zip(a,b))
def mapped_uv(face,old_side,old_lo,old_hi,new_side,new_lo,new_hi,matrix,offset,resolution):
    points=[add(mul(matrix,p),offset) for p in face_vertices(old_lo,old_hi,old_side)]
    canonical=face_vertices(new_lo,new_hi,new_side)
    indices=[next(i for i,p in enumerate(points) if near(p,c)) for c in canonical]
    source=[(u*16/resolution[0],v*16/resolution[1]) for u,v in face_uv(face['uv'],face.get('rotation',0))]
    desired=[source[i] for i in indices]
    u,v,U,V=[face['uv'][i]*16/resolution[i%2] for i in range(4)]
    for flip_u,flip_v,angle in itertools.product((False,True),(False,True),(0,90,180,270)):
        uv=[U if flip_u else u,V if flip_v else v,u if flip_u else U,v if flip_v else V]
        if all(near(a,b) for a,b in zip(face_uv(uv,angle),desired)):
            result={'uv':[round(x,6) for x in uv],'texture':'#0'}
            if angle: result['rotation']=angle
            if 'tint' in face: result['tintindex']=face['tint']
            return result
    raise ValueError('Cannot preserve UV orientation')

def export_cube(cube,matrix,offset,resolution):
    lo,hi=cube['from'],cube['to']
    points=[add(mul(matrix,p),offset) for p in itertools.product(*zip(lo,hi))]
    new_lo=[min(p[i] for p in points) for i in range(3)];new_hi=[max(p[i] for p in points) for i in range(3)]
    result={'from':[round(v,6) for v in new_lo],'to':[round(v,6) for v in new_hi],'faces':{}}
    for side,face in cube['faces'].items():
        if face.get('texture') is None:continue
        if len(set(face_vertices(lo,hi,side)))<4:continue
        direction=mul(matrix,SIDES[side]);new_side=next(n for n,v in SIDES.items() if near(v,direction))
        result['faces'][new_side]=mapped_uv(face,side,lo,hi,new_side,new_lo,new_hi,matrix,offset,resolution)
    angles=cube.get('rotation',[0,0,0]);nonzero=[i for i,v in enumerate(angles) if abs(v)>1e-5]
    if nonzero:
        if len(nonzero)!=1:raise ValueError('Minecraft cube requires one rotation axis')
        axis=nonzero[0];vector=mul(matrix,[int(i==axis) for i in range(3)]);new_axis=next(i for i,v in enumerate(vector) if v)
        angle=angles[axis]*vector[new_axis]
        if angle not in (-45,-22.5,0,22.5,45):raise ValueError('Unsupported cube rotation')
        result['rotation']={'origin':[round(v,6) for v in add(mul(matrix,cube['origin']),offset)],'axis':'xyz'[new_axis],'angle':angle,'rescale':cube.get('rescale',False)}
    if 'shade' in cube:result['shade']=cube['shade']
    return result

def export(source,destination):
    doc=json.loads(Path(source).read_text(encoding='utf-8'));elements={e['uuid']:e for e in doc['elements']};groups={}
    def index(nodes):
        for node in nodes:
            if isinstance(node,dict):groups[node['name']]=node;index(node.get('children',[]))
    index(doc['outliner'])
    texture=doc['textures'][0]['source'];assert texture.startswith('data:image/png;base64,')
    files={'assets/runes/textures/item/effects/life_vines/vine_root.png':base64.b64decode(texture.split(',',1)[1])}
    resolution=[doc['resolution']['width'],doc['resolution']['height']]
    identity=[[1,0,0],[0,1,0],[0,0,1]]
    total=0
    for name,stem_name in zip(PARTS,STEMS):
        stem=groups[stem_name];pivot=stem['origin'];output=[]
        def walk(nodes,matrix,offset):
            for node in nodes:
                if isinstance(node,str):
                    cube=elements[node]
                    if cube.get('visibility',True) and cube.get('export',True):output.append(export_cube(cube,matrix,offset,resolution))
                elif node.get('visibility',True) and node.get('export',True):
                    rot=cardinal(node.get('rotation',[0,0,0]));origin=node['origin']
                    walk(node['children'],mm(matrix,rot),add(offset,mul(matrix,sub(origin,mul(rot,origin)))))
        walk(stem['children'],identity,sub([8,8,8],pivot))
        total+=len(output)
        model={'ambientocclusion':False,'textures':{'0':'runes:item/effects/life_vines/vine_root','particle':'#0'},'elements':output}
        item={'model':{'type':'minecraft:model','model':'runes:effects/life_vines/'+name,'tints':[{'type':'minecraft:constant','value':16777215}]}}
        for kind,value in [('models',model),('items',item)]:files[f'assets/runes/{kind}/effects/life_vines/{name}.json']=(json.dumps(value,indent=2)+'\n').encode()
    assert total==len(elements),f'Not all cubes exported: {total}/{len(elements)}'
    with zipfile.ZipFile(destination,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
        for name,data in sorted(files.items()):archive.writestr(name,data)
    print(f'Exported {len(PARTS)} sections, {total} original cubes, original embedded texture: {destination}')

if __name__=='__main__':
    if len(sys.argv)!=3:raise SystemExit('Usage: python3 export_plant_vines.py model.bbmodel resource-pack-additions.zip')
    export(Path(sys.argv[1]),Path(sys.argv[2]))
