package dev.muuko.honorfreeclip2;

/**
 * Bounded read-only candidate query for 2B/17 (known on some Huawei earbuds).
 * FreeClip 2 TLV01=left and TLV02=right were independently A/B tested.
 * Only 00=wake agent and FF=none are established; other values remain raw.
 */
final class AiKeyProbeCodec {
    static final byte[] QUERY = FreeClipProtocol.hex("5A0007002B170100020030A7");
    static final class State {
        // A/B captured: TLV01=left, TLV02=right. Unknown actions stay raw.
        final int leftRaw;
        final int rightRaw;
        final String verifiedFrame;
        final java.util.Map<Integer, byte[]> tlvs;
        State(int leftRaw,int rightRaw,String verifiedFrame) {
            this(leftRaw,rightRaw,verifiedFrame,java.util.Collections.emptyMap());
        }
        State(int leftRaw,int rightRaw,String verifiedFrame,java.util.Map<Integer,byte[]> values) {
            this.leftRaw=leftRaw;
            this.rightRaw=rightRaw;
            this.verifiedFrame=verifiedFrame;
            java.util.Map<Integer,byte[]> copy=new java.util.TreeMap<>();
            for(java.util.Map.Entry<Integer,byte[]> e:values.entrySet())
                copy.put(e.getKey(),e.getValue().clone());
            tlvs=java.util.Collections.unmodifiableMap(copy);
        }
        String summary() {
            return "AI 键回读：左侧 TLV01=" + hexByte(leftRaw) + "（" + AiKeyActionCodec.actionName(leftRaw) + "）"
                    + "，右侧 TLV02=" + hexByte(rightRaw) + "（" + AiKeyActionCodec.actionName(rightRaw) + "）";
        }
    }
    private static String hexByte(int x) {
        return String.format(java.util.Locale.ROOT,"0x%02X",x & 255);
    }

    /** CRC-verified 2B/17 with exactly one byte each in left/right TLVs. */
    static State parseState(byte[] stream) {
        if(stream == null || stream.length == 0 || stream.length > 64*1024) return null;
        State latest=null;
        int offset=0;
        while(offset+9<=stream.length) {
            if((stream[offset]&255)!=0x5a || stream[offset+1]!=0){offset++;continue;}
            int size=5+(stream[offset+2]&255)+((stream[offset+3]&255)<<8);
            if(size<9 || size>4096 || offset+size>stream.length){offset++;continue;}
            byte[] frame=java.util.Arrays.copyOfRange(stream,offset,offset+size);
            int crc=FreeClipProtocol.crc16(frame,size-2);
            if (((crc>>>8)&255)!=(frame[size-2]&255)||(crc&255)!=(frame[size-1]&255)){
                offset++;continue;
            }
            offset+=size;
            if((frame[4]&255)!=0x2B||(frame[5]&255)!=0x17)continue;
            int end=size-2, at=6;
            Integer left=null,right=null;
            boolean valid=true;
            java.util.HashSet<Integer> ids=new java.util.HashSet<>();
            java.util.Map<Integer,byte[]> fields=new java.util.TreeMap<>();
            while(at<end){
                if(at+2>end){valid=false;break;}
                int type=frame[at++]&255,len=frame[at++]&255;
                if(at+len>end || !ids.add(type)){valid=false;break;}
                fields.put(type,java.util.Arrays.copyOfRange(frame,at,at+len));
                if(type==1){if(len!=1){valid=false;break;}left=frame[at]&255;}
                if(type==2){if(len!=1){valid=false;break;}right=frame[at]&255;}
                at+=len;
            }
            if(valid && at==end && left!=null && right!=null){
                latest=new State(left,right,DirectEqualizerCodec.describe(frame),fields);
            }
        }
        return latest;
    }

    /** Compare every validated TLV, not just the two unconfirmed candidate fields. */
    static String compare(State before, State after) {
        if (before == null || after == null)
            return "缺少可比较的 CRC 有效回包；请分别在操作前后探测。";
        if (!before.tlvs.isEmpty() && !after.tlvs.isEmpty()) {
            java.util.Set<Integer> all=new java.util.TreeSet<>();
            all.addAll(before.tlvs.keySet());
            all.addAll(after.tlvs.keySet());
            StringBuilder delta=new StringBuilder("2B/17 全部 TLV 前后对比：");
            int changes=0;
            for(Integer type:all) {
                byte[] from=before.tlvs.get(type),to=after.tlvs.get(type);
                if(java.util.Arrays.equals(from,to))continue;
                changes++;
                delta.append("\nTLV")
                        .append(String.format(java.util.Locale.ROOT,"%02X",type))
                        .append(": ").append(hexBytes(from))
                        .append(" → ").append(hexBytes(to));
            }
            if(changes==0)delta.append("所有字段均未变化。该回包可能是能力表，尚不能作为 AI 键当前动作依据。");
            else delta.append("\n检测到 ").append(changes)
                    .append(" 个原始字段变化，尚需验证它们与 AI 键动作的对应关系。");
            return delta.toString();
        }
        StringBuilder delta = new StringBuilder("2B/17 候选字段变化（不代表左右耳映射）：");
        if (before.leftRaw != after.leftRaw)
            delta.append(" TLV01 ").append(hexByte(before.leftRaw))
                    .append(" → ").append(hexByte(after.leftRaw)).append(";");
        if (before.rightRaw != after.rightRaw)
            delta.append(" TLV02 ").append(hexByte(before.rightRaw))
                    .append(" → ").append(hexByte(after.rightRaw)).append(";");
        if (before.leftRaw == after.leftRaw && before.rightRaw == after.rightRaw)
            delta.append(" 未检测到候选字段变化。");
        return delta.toString();
    }
    private static String hexBytes(byte[] value) {
        if(value==null)return "(缺失)";
        StringBuilder result=new StringBuilder();
        for(byte b:value)result.append(String.format(java.util.Locale.ROOT,"%02X",b&255));
        return result.toString();
    }
    private AiKeyProbeCodec() { }
    static String summarize(byte[] stream) {
        if (stream == null || stream.length == 0) return "未收到数据（FreeClip 2 可能不支持此查询）";
        int offset=0, valid=0;
        String values="";
        while (offset+9<=stream.length && offset<8192) {
            if ((stream[offset]&255)!=0x5a || stream[offset+1]!=0) {offset++;continue;}
            int size=5+(stream[offset+2]&255)+((stream[offset+3]&255)<<8);
            if (size<9 || size>4096 || offset+size>stream.length) {offset++;continue;}
            byte[] frame=java.util.Arrays.copyOfRange(stream,offset,offset+size);
            int crc=FreeClipProtocol.crc16(frame,size-2);
            if (((crc>>8)&255)!=(frame[size-2]&255)||(crc&255)!=(frame[size-1]&255)){
                offset++;continue;
            }
            offset+=size;
            if ((frame[4]&255)!=0x2b||(frame[5]&255)!=0x17)continue;
            valid++;
            int at=6,end=size-2;
            StringBuilder raw=new StringBuilder();
            while (at+2<=end){
                int type=frame[at++]&255, len=frame[at++]&255;
                if(at+len>end){raw.append("异常字段");break;}
                if(raw.length()>0)raw.append(" ");
                raw.append(String.format(java.util.Locale.ROOT,"TLV%02X=",type));
                for(int j=0;j<len && j<20;j++)raw.append(String.format(java.util.Locale.ROOT,"%02X",frame[at+j]&255));
                at+=len;
            }
            String frameHex = DirectEqualizerCodec.describe(java.util.Arrays.copyOf(frame,
                    Math.min(frame.length, 128)));
            if (frame.length > 128) frameHex += "…（已截断，最多展示 128 字节）";
            values = raw.toString() + "\nCRC 验证后的整帧 HEX：" + frameHex;
        }
        if(valid==0)return "无有效 2B/17 状态帧；无法读取 AI 键动作";
        State parsed = parseState(stream);
        return "收到 " + valid + " 个 CRC 正确的 2B/17 帧：" + values
                + (parsed == null ? "\n未确认左右耳字段。" : "\n" + parsed.summary())
                + "。已验证 00=唤醒智能体、FF=无；其他动作码尚待验证。";
    }
}
