package dev.muuko.honorfreeclip2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Three-slot policy for Huawei 2B/4A catalog and 2B/49 writes.
 * Current-slot changes were verified by the user; create and delete are NEW paths
 * known from OpenFreebuds but not yet confirmed on the FreeClip 2 firmware.
 * All mutations require an authoritative TLV08 snapshot, strict preflight, and
 * a separately CRC-verified post-write readback. No optimistic state creation.
 */
final class CustomPresetPolicy {
    static final int FIRST = 0x64, LAST = 0x66, MAX = 3;
    private CustomPresetPolicy() { }
    static boolean validId(int id) {return id >= FIRST && id <= LAST;}
    static EqualizerState.Preset lookup(EqualizerState state,int id) {
        if (state == null) return null;
        for (EqualizerState.Preset p: state.customPresets) if (p.id == id) return p;
        return null;
    }
    static boolean validCatalog(EqualizerState s) {
        if (s == null || !s.supported || !s.customCatalogKnown
                || s.customPresets.size()>MAX) return false;
        Set<Integer> ids=new HashSet<>();
        for (EqualizerState.Preset p:s.customPresets) {
            if (!validId(p.id) || !ids.add(p.id) || !EqSavePolicy.validName(p.name)
                    || !EqSavePolicy.validGains(EqSavePolicy.toArray(p.gains))) return false;
        }
        if (validId(s.selectedId) && lookup(s,s.selectedId)==null) return false;
        return true;
    }
    static int firstEmpty(EqualizerState s) {
        if (!validCatalog(s)) return -1;
        for (int i=FIRST;i<=LAST;i++) if(lookup(s,i)==null)return i;
        return -1;
    }
    static boolean nameFree(EqualizerState s,int target,String name) {
        if(!EqSavePolicy.validName(name))return false;
        for(EqualizerState.Preset p:s.customPresets) if(p.id!=target
                && p.name.trim().equalsIgnoreCase(name.trim()))return false;
        return true;
    }
    static boolean createAllowed(EqualizerState s,int id,String name,int[] gains) {
        return validCatalog(s) && validId(id) && lookup(s,id)==null
                && nameFree(s,id,name) && EqSavePolicy.validGains(gains);
    }
    static boolean switchAllowed(EqualizerState s,int id) {
        return validCatalog(s) && validId(id) && s.selectedId!=id && lookup(s,id)!=null;
    }
    static boolean deleteAllowed(EqualizerState s,int id) {
        // Require switching to a different preset first; deletion is irreversible.
        return validCatalog(s) && validId(id) && s.selectedId!=id && lookup(s,id)!=null;
    }
    /** No hidden concurrent writes to other slots are tolerated. */
    static boolean sameCatalog(EqualizerState before,EqualizerState after) {
        if(!validCatalog(before)||!validCatalog(after)
                || before.selectedId!=after.selectedId
                || !Objects.equals(before.selectedName,after.selectedName)
                || !Objects.equals(before.selectedGains,after.selectedGains)
                || !before.builtInIds.equals(after.builtInIds)
                || before.customPresets.size()!=after.customPresets.size())return false;
        for(EqualizerState.Preset p:before.customPresets){
            EqualizerState.Preset next=lookup(after,p.id);
            if(next==null || !p.name.equals(next.name) || !p.gains.equals(next.gains))return false;
        }
        return true;
    }
    static boolean created(EqualizerState before,EqualizerState after,int id,String name,int[] gains) {
        if(!createAllowed(before,id,name,gains)||!validCatalog(after)
                || after.customPresets.size()!=before.customPresets.size()+1
                || after.selectedId!=id)return false;
        EqualizerState.Preset found=lookup(after,id);
        if(found==null||!found.name.equals(name.trim())
                ||!Arrays.equals(EqSavePolicy.toArray(found.gains),gains))return false;
        for(EqualizerState.Preset p:before.customPresets){
            EqualizerState.Preset q=lookup(after,p.id);
            if(q==null||!q.name.equals(p.name)||!q.gains.equals(p.gains))return false;
        }
        return true;
    }
    static boolean switched(EqualizerState before,EqualizerState after,int id) {
        if(!switchAllowed(before,id)||!validCatalog(after)||after.selectedId!=id
                ||before.customPresets.size()!=after.customPresets.size())return false;
        for(EqualizerState.Preset p:before.customPresets){
            EqualizerState.Preset q=lookup(after,p.id);
            if(q==null||!q.name.equals(p.name)||!q.gains.equals(p.gains))return false;
        }
        EqualizerState.Preset selected=lookup(after,id);
        return selected!=null && Objects.equals(after.selectedName,selected.name)
                && Objects.equals(after.selectedGains,selected.gains);
    }
    static boolean deleted(EqualizerState before,EqualizerState after,int id) {
        if(!deleteAllowed(before,id)||!validCatalog(after) ||after.selectedId==id
                ||after.customPresets.size()!=before.customPresets.size()-1
                ||lookup(after,id)!=null)return false;
        for(EqualizerState.Preset p:before.customPresets){
            if(p.id==id)continue;
            EqualizerState.Preset q=lookup(after,p.id);
            if(q==null||!q.name.equals(p.name)||!q.gains.equals(p.gains))return false;
        }
        return true;
    }
}
