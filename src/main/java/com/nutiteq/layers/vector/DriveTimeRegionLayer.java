package com.nutiteq.layers.vector;

import java.util.LinkedList;
import java.util.Vector;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.net.ParseException;
import android.net.Uri;

import com.nutiteq.components.Envelope;
import com.nutiteq.components.MapPos;
import com.nutiteq.geometry.Geometry;
import com.nutiteq.geometry.Polygon;
import com.nutiteq.log.Log;
import com.nutiteq.projections.Projection;
import com.nutiteq.style.PolygonStyle;
import com.nutiteq.style.StyleSet;
import com.nutiteq.tasks.CancelableThreadPool;
import com.nutiteq.tasks.Task;
import com.nutiteq.ui.DefaultLabel;
import com.nutiteq.utils.NetUtils;
import com.nutiteq.vectorlayers.GeometryLayer;

/**
 * Shows Drive Time region for given location
 * @author jaak
 *
 */
public class DriveTimeRegionLayer extends GeometryLayer {

    private StyleSet<PolygonStyle> polygonStyleSet;
    private MapPos mapPos;
    private float distance;
    private LinkedList<Geometry> currentVisibleElementsList = new LinkedList<Geometry>();
    private final CancelableThreadPool dataPool = new CancelableThreadPool(1);

    /**
     * Custom task for loading data in background.
     */
    protected class LoadDataTask implements Task {
        @Override
        public void run() {
            loadData(mapPos, distance);
        }

        @Override
        public boolean isCancelable() {
            return true;
        }

        @Override
        public void cancel() {
        }
    }


    public DriveTimeRegionLayer(Projection proj, StyleSet<PolygonStyle> polygonStyleSet) {
        super(proj);
        this.polygonStyleSet = polygonStyleSet;
    }

    @Override
    public void calculateVisibleElements(Envelope envelope, int zoom) {
        if(currentVisibleElementsList == null)
            return;

        // set style for elements, so they will be visible now
        for(Geometry element : currentVisibleElementsList){
            element.setActiveStyle(zoom);
        }

        setVisibleElements(currentVisibleElementsList);
    }

    protected void loadData(MapPos pos, float distance) {
        if (pos == null){
            return;
        }

        // Notify we started 
        long timeStart = System.currentTimeMillis();

        setRunningState(true);

        // load geometries
        try {
            Uri.Builder uri = Uri.parse("http://kaart.maakaart.ee/dd/").buildUpon();
            uri.appendQueryParameter("output", "geojson");
            uri.appendQueryParameter("lat", Double.toString(pos.y));
            uri.appendQueryParameter("lon", Double.toString(pos.x));
            uri.appendQueryParameter("dist", Float.toString(distance));

            Log.debug("DriveTime API url:" + uri.build().toString());

            JSONObject jsonData = NetUtils.getJSONFromUrl(uri.build().toString());

            if(jsonData == null){
                Log.debug("No DriveTime data");
                return;
            }

            JSONArray rows = jsonData.getJSONArray("features");

            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);

                int minutes = (int)(Float.parseFloat(row.getJSONObject("properties").getString("name"))*60.0f);
                JSONArray coords = row.getJSONObject("geometry").getJSONArray("coordinates").getJSONArray(0);
                Vector<MapPos> mapPos = new Vector<MapPos>();

                for(int j = 0; j < coords.length(); j++){
                    mapPos.add(projection.fromWgs84(coords.getJSONArray(j).getDouble(0),coords.getJSONArray(j).getDouble(1)));
                }

                String distLabel =  minutes + " min";

                if(minutes>=60){
                    int hours = minutes / 60; //since both are ints, you get an int
                    int min = minutes % 60;
                    distLabel  = String.format("%d:%02d",hours, min)+ " h";
                }

                Geometry newObject = new Polygon(mapPos, new DefaultLabel(distLabel,"drivetime region"), polygonStyleSet, null);

                newObject.attachToLayer(this);
                currentVisibleElementsList.clear();
                currentVisibleElementsList.add(newObject);
            }
        }
        catch (ParseException e) {
            Log.error("Error parsing data " + e.toString());
        } catch (JSONException e) {
            Log.error("Error parsing JSON data " + e.toString());
        }

        // Notify we have stopped
        long timeEnd = System.currentTimeMillis();
        Log.debug("DriveTimeRegionLayer: loaded dist:" + distance + " h, calc time ms:" + (timeEnd-timeStart));

        setRunningState(false);

        // refresh map: eventually calculateVisibleElements will be called
        updateVisibleElements();
    }

    public void setMapPos(MapPos mapPos) {
        this.mapPos = mapPos;
        dataPool.cancelAll();
        dataPool.execute(new LoadDataTask());
    }

    public void setDistance(float distance){
        this.distance = distance;
        dataPool.cancelAll();
        dataPool.execute(new LoadDataTask());
    }
    
    protected void setRunningState(boolean flag) {
    }

}
