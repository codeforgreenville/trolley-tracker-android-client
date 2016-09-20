package com.codeforgvl.trolleytrackerclient.helpers;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;

import com.codeforgvl.trolleytrackerclient.Constants;
import com.codeforgvl.trolleytrackerclient.R;
import com.codeforgvl.trolleytrackerclient.data.TrolleyAPI;
import com.codeforgvl.trolleytrackerclient.fragments.TrackerFragment;
import com.codeforgvl.trolleytrackerclient.models.json.Trolley;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.joda.time.DateTime;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by ahodges on 12/18/2015.
 */
public class TrolleyManager {
    private static final String NOTIFIED_EMPTY_KEY = "NOTIFIED_EMPTY";
    private TrackerFragment trackerFragment;
    private HashMap<Integer, Marker> trolleyMarkers = new HashMap<>();
    private TrolleyUpdateTask mUpdateTask;
    private boolean notifiedEmpty = false;

    private Trolley[] lastTrolleyUpdate;
    private Long lastUpdatedAt;

    public TrolleyManager(TrackerFragment activity){
        trackerFragment = activity;
    }

    public void processBundle(Bundle b){
        if(b == null){
            return;
        }
        if (b != null){
            lastUpdatedAt = b.getLong(Trolley.LAST_UPDATED_KEY);
            DateTime lastUpdate = new DateTime(lastUpdatedAt);
            if(!lastUpdate.isBefore(DateTime.now().minusMinutes(1))){
                Parcelable[] tParcels = b.getParcelableArray(Trolley.TROLLEY_KEY);
                lastTrolleyUpdate = new Trolley[tParcels.length];
                System.arraycopy(tParcels, 0, lastTrolleyUpdate, 0, tParcels.length);
                updateTrolleys(lastTrolleyUpdate);
            }

            notifiedEmpty = b.getBoolean(TrolleyManager.NOTIFIED_EMPTY_KEY, false);
        }
    }

    public void onMapReady(){
        if(lastTrolleyUpdate != null){
            updateTrolleys(lastTrolleyUpdate);
        }
    }

    public Collection<Marker> getMarkers(){
        return trolleyMarkers.values();
    }

    public boolean isEmpty(){
        return trolleyMarkers.isEmpty();
    }

    public void startUpdates(){
        if (mUpdateTask == null || mUpdateTask.isCancelled()){
            mUpdateTask = new TrolleyUpdateTask();
            mUpdateTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    public void stopUpdates(){
        if(mUpdateTask != null){
            mUpdateTask.cancel(false);
        }
    }

    private synchronized void updateTrolleys(Trolley[] trolleys){
        if(trackerFragment.getMap() == null)
            return;

        Set<Integer> keySet = new HashSet<>(trolleyMarkers.keySet());
        for(int i=0; i < trolleys.length; i++){
            Trolley t = trolleys[i];
            if(trolleyMarkers.containsKey(t.ID)){
                trolleyMarkers.get(t.ID).setPosition(new LatLng(t.Lat, t.Lon));
                keySet.remove(t.ID);
            } else {
                trolleyMarkers.put(t.ID, trackerFragment.mMap.addMarker(new MarkerOptions()
                        .anchor(0.5f, 1.0f)
                        .title("Trolley " + t.ID)
                        .icon(BitmapDescriptorFactory.fromResource(((t.ID - 1) % 2 == 0) ? R.drawable.marker1 : R.drawable.marker2))
                        .position(new LatLng(t.Lat, t.Lon))));
            }
        }

        for(Integer deadTrolleyID : keySet){
            trolleyMarkers.get(deadTrolleyID).remove();
            trolleyMarkers.remove(deadTrolleyID);
        }

        if(trolleyMarkers.isEmpty()){
            if(!notifiedEmpty){
                trackerFragment.showNoTrolleysDialog();
                notifiedEmpty = true;
            }
        } else {
            notifiedEmpty = false;
        }

        lastTrolleyUpdate = trolleys;
        lastUpdatedAt = DateTime.now().getMillis();
    }

    private class TrolleyUpdateTask extends AsyncTask<Void, Trolley[], Void> {
        @Override
        protected Void doInBackground(Void... params) {
            while (!isCancelled()) {
                Log.d(Constants.LOG_TAG, "requesting trolley update");

                Trolley[] trolleyList = TrolleyAPI.getRunningTrolleys();

                publishProgress(trolleyList);

                try {
                    Thread.sleep(Constants.SLEEP_INTERVAL);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Trolley[]... trolleyUpdate){
            Log.d(Constants.LOG_TAG, "processing trolley updates");

            updateTrolleys(trolleyUpdate[0]);
        }
    }

    public void saveInstanceState(Bundle savedInstanceState){
        savedInstanceState.putParcelableArray(Trolley.TROLLEY_KEY, lastTrolleyUpdate);
        savedInstanceState.putBoolean(NOTIFIED_EMPTY_KEY, notifiedEmpty);
        savedInstanceState.putLong(Trolley.LAST_UPDATED_KEY, lastUpdatedAt);
    }
}
