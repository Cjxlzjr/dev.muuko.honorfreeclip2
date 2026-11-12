package dev.muuko.honorfreeclip2;
public final class AiKeyProbeSelfTest {
    public static void main(String[] args) {
        byte[] body=FreeClipProtocol.hex("2B17010100020101");
        int len=body.length+1;
        byte[] frame=new byte[4+body.length+2];
        frame[0]=0x5A;frame[1]=0;frame[2]=(byte)len;frame[3]=(byte)(len>>>8);
        System.arraycopy(body,0,frame,4,body.length);
        int crc=FreeClipProtocol.crc16(frame,frame.length-2);
        frame[frame.length-2]=(byte)(crc>>>8);frame[frame.length-1]=(byte)crc;
        String valid=AiKeyProbeCodec.summarize(frame);
        if(!valid.contains("TLV01=00")||!valid.contains("TLV02=01")
                || !valid.contains("整帧 HEX："))
            throw new AssertionError(valid);
        frame[8]^=1;
        if(AiKeyProbeCodec.summarize(frame).contains("收到 1"))
            throw new AssertionError("accepted corrupt CRC");
        System.out.println("AiKeyProbeSelfTest passed");
    }
}
