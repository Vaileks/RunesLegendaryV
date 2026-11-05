package dev.vaileks.runeslegendaryv.weapon.mechzhizni;
import org.bukkit.util.Transformation;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
/** Recreated binding animation. Original server stem/leaf meshes and texture are bundled separately. */
public final class PlantVineRig {
 private PlantVineRig(){}
 public static final String[] PARTS={"vine","vine","vine","vine","vine2","vine2","vine2","vine2"};
 private static double clamp(double value){return Math.max(0,Math.min(1,value));}
 private static double smooth(double value){value=clamp(value);return value*value*(3-2*value);}
 public static double progress(double age,double duration,double grow,double sink){
  if(!Double.isFinite(duration)||duration<=0)return 0;
  age=Math.max(0,Math.min(duration,age));
  grow=Math.min(duration*.5,Math.max(.05,Double.isFinite(grow)?grow:1));
  sink=Math.min(duration*.5,Math.max(.05,Double.isFinite(sink)?sink:1));
  return smooth(age<grow?age/grow:age>duration-sink?(duration-age)/sink:1);
 }
 public static Vector3f node(int side,int joint,double progress,float scale){
  double rise=smooth(progress/.65),curl=smooth((progress-.15)/.85);
  double radius=.44-.05*curl,angle=side*Math.PI+joint*.8*curl;
  double size=scale/1.35f;
  return new Vector3f((float)(Math.cos(angle)*radius*size),(float)((joint*.24-1.3*(1-rise))*size),(float)(Math.sin(angle)*radius*size));
 }
 public static Transformation transform(int part,double progress,float scale){
  int side=part/4,joint=part%4;
  Vector3f start=node(side,joint,progress,scale),end=node(side,joint+1,progress,scale);
  Vector3f y=new Vector3f(end).sub(start);float length=y.length();y.normalize();
  // The leaf's local +Z faces away from the legs. The right rotation cancels
  // Minecraft ItemDisplay's built-in Y=180 degree item render rotation.
  Vector3f z=new Vector3f(start).add(end).setComponent(1,0).normalize();
  z.sub(new Vector3f(y).mul(z.dot(y))).normalize();
  Vector3f x=new Vector3f(y).cross(z).normalize();
  Quaternionf rotation=new Quaternionf().setFromNormalized(new Matrix3f().setColumn(0,x).setColumn(1,y).setColumn(2,z));
  float width=.65f*(scale/1.35f);
  return new Transformation(start,rotation,new Vector3f(width,length/(9f/16f),width),new Quaternionf().rotateY((float)Math.PI));
 }
}
