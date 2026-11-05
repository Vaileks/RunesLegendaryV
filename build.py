from pathlib import Path
from zipfile import ZipFile,ZIP_DEFLATED
import os,subprocess,shutil
root=Path(__file__).resolve().parent
api=Path(os.environ.get('PAPER_API',str(Path.home()/'.m2/repository/io/papermc/paper/paper-api/1.21.11-R0.1-SNAPSHOT/paper-api-1.21.11-R0.1-SNAPSHOT.jar')))
cp=[api]+[p for p in (Path.home()/'.m2/repository').rglob('*.jar') if any(x in str(p) for x in ['/net/kyori/','/org/joml/','/com/google/guava/','/org/jetbrains/annotations/','/org/jspecify/','/net/md-5/bungeecord-chat/']) and '-sources' not in p.name and '-javadoc' not in p.name]
java=Path(os.environ['JAVA_HOME'])/'bin/javac' if os.environ.get('JAVA_HOME') else Path(shutil.which('javac') or 'javac')
out=root/'build/classes';shutil.rmtree(out,ignore_errors=True);out.mkdir(parents=True)
subprocess.run([str(java),'--release','21','-proc:none','-encoding','UTF-8','-g:none','-cp',os.pathsep.join(map(str,cp)),'-d',str(out)]+[str(p) for p in sorted((root/'src/main/java').rglob('*.java'))],check=True)
with ZipFile(root/'RunesLegendaryV.jar','w',ZIP_DEFLATED)as z:
 for base in [out,root/'src/main/resources']:
  for p in sorted(base.rglob('*')):
   if p.is_file():z.writestr(p.relative_to(base).as_posix(),p.read_bytes())
with ZipFile(root/'RunesLegendaryV.jar')as z:assert z.testzip()is None
print(root/'RunesLegendaryV.jar')
