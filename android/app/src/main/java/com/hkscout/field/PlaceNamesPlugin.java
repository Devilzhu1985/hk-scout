package com.hkscout.field;
import android.location.*;
import com.getcapacitor.*;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.util.*;

@CapacitorPlugin(name="PlaceNames")
public class PlaceNamesPlugin extends Plugin {
    @PluginMethod public void lookup(PluginCall call){
        Double lat=call.getDouble("lat"),lng=call.getDouble("lng");
        if(lat==null||lng==null||!Double.isFinite(lat)||!Double.isFinite(lng)||Math.abs(lat)>90||Math.abs(lng)>180){call.reject("Invalid coordinates");return;}
        if(!Geocoder.isPresent()){call.resolve(new JSObject());return;}
        // Off the UI thread. Android's provider may need network and may return
        // only a nearby street; never claim this is a verified business name.
        new Thread(()->{try{
            Geocoder coder=new Geocoder(getContext(),"zh".equals(call.getString("language"))?Locale.SIMPLIFIED_CHINESE:Locale.ENGLISH);
            List<Address> addresses=coder.getFromLocation(lat,lng,1);JSObject out=new JSObject();
            if(addresses!=null&&!addresses.isEmpty()){Address a=addresses.get(0);String street=a.getThoroughfare(),area=a.getSubLocality(),city=a.getLocality();String name=street!=null?street:area!=null?area:city;
                if(name!=null)out.put("name",name);if(city!=null)out.put("city",city);out.put("source","Android Geocoder");out.put("approximate",true);}
            call.resolve(out);
        }catch(Exception e){call.resolve(new JSObject());}},"ScoutPlaceName").start();
    }
}
