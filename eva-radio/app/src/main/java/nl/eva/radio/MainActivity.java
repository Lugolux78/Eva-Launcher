package nl.eva.radio;
import android.app.*;
import android.content.*;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.*;
import java.util.*;
import org.json.JSONObject;

/** Evadio Walkman activity, isolated from radio service. */
public final class MainActivity extends Activity implements WalkmanView.Controller {
    private WalkmanView walkman;
    private SharedPreferences prefs;
    private StationRepository repo;
    private final List<Station> stations = new ArrayList<>();
    private Station selected;
    private boolean playing, buffering, registered;
    private AudioManager audio;
    private final BroadcastReceiver updates = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            playing=i.getBooleanExtra("playing",false);
            buffering=i.getBooleanExtra("buffering",false);
            String name=i.getStringExtra("name");
            if (selected!=null && (name==null || name.equals("Evadio"))) name=selected.name;
            walkman.setPlayback(name,i.getStringExtra("status"),playing,buffering);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|
              View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_FULLSCREEN);
        prefs=getSharedPreferences("radio",MODE_PRIVATE);
        repo=new StationRepository();
        audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        walkman=new WalkmanView(this,this);
        setContentView(walkman);
        refreshSelected(); refreshFavorites(); refreshVolume();
        repo.search("NL","",new StationRepository.Callback(){
            @Override public void onResult(List<Station> r){
                runOnUiThread(()->{stations.clear();stations.addAll(r);});
            }
            @Override public void onError(String error) {}
        });
    }
    private void refreshSelected() {
        String raw=prefs.getString("lastStation","");
        if(!raw.isEmpty()) try{
            selected=Station.fromJson(new JSONObject(raw));
            walkman.setPlayback(selected.name,"Gereed",playing,buffering);
        }catch(Exception ignored){}
    }
    private void refreshFavorites() {
        try{walkman.setFavoritesCount(new JSONObject(prefs.getString("favorites","{}")).length());}
        catch(Exception e){walkman.setFavoritesCount(0);}
    }
    private void refreshVolume() {
        int max=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        walkman.setVolume(max<=0?0.5f:audio.getStreamVolume(AudioManager.STREAM_MUSIC)/(float)max);
    }
    @Override protected void onStart(){
        super.onStart();
        IntentFilter filter=new IntentFilter(RadioService.STATE_CHANGED);
        if(android.os.Build.VERSION.SDK_INT>=33)
            registerReceiver(updates,filter,Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(updates,filter);
        registered=true;
        startService(new Intent(this,RadioService.class).setAction(RadioService.STATUS));
    }
    @Override protected void onResume() {super.onResume();refreshSelected();refreshFavorites();refreshVolume();}
    @Override protected void onStop() {if(registered){unregisterReceiver(updates);registered=false;}super.onStop();}
    @Override protected void onDestroy(){repo.shutdown();super.onDestroy();}
    private void play(Station st) {
        if(st==null||!st.playable())return;
        selected=st;
        prefs.edit().putString("lastStation",st.toJson().toString()).apply();
        walkman.setPlayback(st.name,"Verbinden…",false,true);
        Intent i=new Intent(this,RadioService.class).setAction(RadioService.PLAY);
        i.putExtra("name",st.name).putExtra("url",st.stream);
        startForegroundService(i);
    }
    @Override public void onPlayPause(){
        if(playing||buffering)startService(new Intent(this,RadioService.class).setAction(RadioService.TOGGLE));
        else if(selected!=null)play(selected);
        else onBrowse(false);
    }
    @Override public void onStopPlayback(){startService(new Intent(this,RadioService.class).setAction(RadioService.STOP));}
    private void skip(int dir){
        if(stations.isEmpty()){onBrowse(false);return;}
        int idx=-1;
        if(selected!=null)for(int n=0;n<stations.size();n++)
            if(stations.get(n).id.equals(selected.id)){idx=n;break;}
        play(stations.get(idx<0?0:(idx+dir+stations.size())%stations.size()));
    }
    @Override public void onPrevious(){skip(-1);}
    @Override public void onNext(){skip(1);}
    @Override public void onBrowse(boolean favorites){
        startActivity(new Intent(this,ClassicActivity.class).putExtra("favorites",favorites));
    }
    @Override public void onSettings(){
        new AlertDialog.Builder(this).setTitle("Evadio Walkman")
        .setItems(new String[]{"Zenders zoeken","Favorieten","Over Evadio"},(d,n)->{
            if(n<=1)onBrowse(n==1);
            else new AlertDialog.Builder(this).setTitle("Evadio v0.3")
                 .setMessage("Dynamisch cassettelabel, bewegende spoelen en echte internetradio.")
                 .setPositiveButton("OK",null).show();
        }).show();
    }
    @Override public void onVolume(float f){
        audio.setStreamVolume(AudioManager.STREAM_MUSIC,
           Math.round(Math.max(0f,Math.min(1f,f))*audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)),0);
    }
}
