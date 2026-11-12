package android.bluetooth;
import java.io.*;
import java.util.Arrays;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import java.nio.charset.StandardCharsets;
public class BluetoothSocket implements Closeable {
  private boolean connected;
  private final PipedInputStream input;
  private final PipedOutputStream reply;
  public BluetoothSocket() {
    try { input = new PipedInputStream(32768); reply = new PipedOutputStream(input); }
    catch (IOException e) { throw new RuntimeException(e); }
  }
  public void connect() throws IOException {connected=true;}
  public boolean isConnected(){return connected;}
  public InputStream getInputStream() {return input;}
  public OutputStream getOutputStream() {
    return new OutputStream() {
      @Override public void write(int v) throws IOException {write(new byte[]{(byte)v});}
      @Override public void write(byte[] data,int off,int len) throws IOException {write(Arrays.copyOfRange(data,off,off+len));}
      @Override public void write(byte[] command) throws IOException {
        if(!connected) throw new IOException("closed");
        byte[] out;
        if(command.length>100) {
          out=concat(
              frame(0x2B,0x11,1,1,1),
              frame(0x2B,0xB4,1,1,7,2,1,1),
              frame(0x2B,0xB4,1,1,2,2,1,0),
              frame(0x2B,0xB4,1,1,0x18,2,1,2,3,1,0),
              eq(),
              frame(1,0x20,1,1,1,2,1,2),
              frame(1,0x26,1,1,2,2,1,7));
        } else if (command.length == 10 && (command[4]&255)==0x2B && (command[5]&255)==0x11) {
          out=frame(0x2B,0x11,1,1,1);
        } else if (command.length==14 && (command[4]&255)==0x2B && (command[5]&255)==0xB4 && command[8]==7 && command[9]==2 && command[10]==1) {
          currentDrop=command[11]&255; out=new byte[0];
        } else if (command.length==13 && (command[4]&255)==0x2B && (command[5]&255)==0xB4 && command[8]==7) {
          out=frame(0x2B,0xB4,1,1,7,2,1,currentDrop);
        } else if (command.length==10 && (command[4]&255)==0x2B && (command[5]&255)==0x4A) {
          out=eq();
        } else if (command.length==12 && (command[4]&255)==0x2B && (command[5]&255)==0x17) {
          out=ai();
        } else if (command.length>=11 && (command[4]&255)==0x2B && (command[5]&255)==0x49) {
          if (multiEqMode) applyCustomCommand(command);
          out=new byte[0];
        } else if (command.length==11 && (command[4]&255)==0x2B && (command[5]&255)==0x16) {
          if((command[6]&255)==1) currentAiLeft=command[8]&255;
          if((command[6]&255)==2) currentAiRight=command[8]&255;
          out=new byte[0];
        } else { out=new byte[0]; }
        reply.write(out);reply.flush();
      }
    };
  }
  private static int currentDrop=0;
  private static int currentAiLeft=0;
  private static int currentAiRight=0xFF;
  private static byte[] ai() {
    return frame(0x2B,0x17,1,1,currentAiLeft,2,1,currentAiRight,
       3,16,0,1,2,3,4,5,6,7,8,9,10,14,15,17,20,255,
       4,1,255,5,1,255,6,2,0,255,9,1,0,10,1,0);
  }
  private static boolean multiEqMode=false;
  private static int multiSelected=100;
  private static final Map<Integer, MockEq> multiPresets = new LinkedHashMap<>();
  private static class MockEq {
    final String name;
    final byte[] gains;
    MockEq(String name,byte[] gains){this.name=name;this.gains=gains.clone();}
  }
  public static void enableMultiEq(){
    multiEqMode=true;multiSelected=100;multiPresets.clear();
    multiPresets.put(100,new MockEq("八音盒",new byte[]{2,-4,0,9,-2,1,5,12,0,-8}));
  }
  private static void applyCustomCommand(byte[] command){
    // Only a test simulator: parse exact valid command format produced by module.
    if(command.length<25)return;
    if((command[6]&255)!=1||(command[7]&255)!=1)return;
    int id=command[8]&255;
    if(id<100||id>102)return;
    if((command[12]&255)!=5||(command[13]&255)!=1)return;
    int op=command[14]&255;
    if((command[15]&255)!=3||(command[16]&255)!=10)return;
    byte[] g=Arrays.copyOfRange(command,17,27);
    if((command[27]&255)!=4)return;
    int nameLength=command[28]&255;
    if(command.length<29+nameLength+2)return;
    String name=new String(command,29,nameLength,StandardCharsets.UTF_8);
    if(op==1){multiPresets.put(id,new MockEq(name,g));multiSelected=id;}
    if(op==2){multiPresets.remove(id);}
  }
  private static byte[] customEqReply(){
    List<Integer> raw=new ArrayList<>();
    add(raw,0x2B,0x4A,1,1,1,2,1,multiSelected,3,4,1,10,3,9,5,1,10);
    // selected curve/name are optional because the custom record is authoritative.
    List<Integer> catalog=new ArrayList<>();
    for(Map.Entry<Integer,MockEq> e:multiPresets.entrySet()){
      catalog.add(e.getKey());catalog.add(10);
      for(byte b:e.getValue().gains)catalog.add(b&255);
      byte[] name=e.getValue().name.getBytes(StandardCharsets.UTF_8);
      for(int i=0;i<24;i++)catalog.add(i<name.length?(name[i]&255):0);
    }
    raw.add(8);raw.add(catalog.size());raw.addAll(catalog);
    int[] data=new int[raw.size()];for(int i=0;i<data.length;i++)data[i]=raw.get(i);
    return frame(data);
  }
  private static void add(List<Integer> items,int... vals){for(int v:vals)items.add(v);}
  private static byte[] eq() {
    if(multiEqMode)return customEqReply();
    return frame(0x2B,0x4A,1,1,1,2,1,1,3,4,1,10,3,9,
      5,1,10,6,10,0,0,0,0,0,0,0,0,0,0,8,0);
  }
  private static byte[] concat(byte[]... data) {
    int len=0;for(byte[] v:data)len+=v.length;
    byte[] out=new byte[len];int p=0;
    for(byte[] v:data){System.arraycopy(v,0,out,p,v.length);p+=v.length;}
    return out;
  }
  private static byte[] frame(int... raw) {
    byte[] b=new byte[raw.length+4];b[0]=0x5A;b[2]=(byte)(raw.length+1);
    for(int i=0;i<raw.length;i++) b[4+i]=(byte)raw[i];
    int crc=0;for(byte byt:b){crc ^= (byt&255)<<8;
      for(int i=0;i<8;i++)crc=((crc&0x8000)!=0 ? (crc<<1)^0x1021 : crc<<1)&0xFFFF;}
    return concat(b,new byte[]{(byte)(crc>>>8),(byte)crc});
  }
  public void close() throws IOException {connected=false;reply.close();input.close();}
}
