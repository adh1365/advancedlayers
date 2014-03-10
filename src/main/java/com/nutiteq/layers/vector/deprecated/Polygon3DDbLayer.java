package com.nutiteq.layers.vector.deprecated;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import com.nutiteq.components.Envelope;
import com.nutiteq.components.MapPos;
import com.nutiteq.db.DBLayer;
import com.nutiteq.db.SpatialLiteDbHelper;
import com.nutiteq.geometry.Geometry;
import com.nutiteq.geometry.Polygon;
import com.nutiteq.geometry.Polygon3D;
import com.nutiteq.log.Log;
import com.nutiteq.projections.EPSG3857;
import com.nutiteq.style.Polygon3DStyle;
import com.nutiteq.style.StyleSet;
import com.nutiteq.vectorlayers.Polygon3DLayer;

/**
 * Simple layer of 3D Polygons, reads data from Spatialite table with 2D polygons
 * 
 * @author jaak
 *
 */
@Deprecated
public class Polygon3DDbLayer extends Polygon3DLayer {
    private static final float DEFAULT_HEIGHT = 2.0f;
    private SpatialLiteDbHelper spatialLite;
    private SpatialLiteDbHelper.DbLayer dbLayer;

    private StyleSet<Polygon3DStyle> styleSet;

    private int minZoom;
    private String heightColumnName;
    private float heightFactor;
    private int maxObjects;

    /**
     * Default constructor.
     * 
     * @param dbPath Spatialite format database
     * @param tableName table with data
     * @param geomColumnName column in table with geometry
     * @param heightColumnName column in table with heigth in meters
     * @param heightFactor multiply height with this number to make it visible
     * @param maxObjects limit number of loaded objects, set to 500 e.g. to avoid out of memory
     * @param styleSet Polygon3DStyle styleset for visualisation
     * @throws IOException 
     */
    public Polygon3DDbLayer(String dbPath, String tableName, String geomColumnName, String heightColumnName,
            float heightFactor, int maxObjects, StyleSet<Polygon3DStyle> styleSet) throws IOException {
        super(new EPSG3857());
        this.styleSet = styleSet;
        this.heightColumnName = heightColumnName;
        this.heightFactor = heightFactor;
        this.maxObjects = maxObjects;
        minZoom = styleSet.getFirstNonNullZoomStyleZoom();
        spatialLite = new SpatialLiteDbHelper(dbPath);
        Map<String, SpatialLiteDbHelper.DbLayer> dbLayers = spatialLite.qrySpatialLayerMetadata();
        for (String layerKey : dbLayers.keySet()) {
            SpatialLiteDbHelper.DbLayer layer = dbLayers.get(layerKey);
            if (layer.table.compareTo(tableName) == 0 && layer.geomColumn.compareTo(geomColumnName) == 0) {
                this.dbLayer = layer;
                break;
            }
        }

        if (this.dbLayer == null) {
            Log.error("Polygon3DDbLayer: Could not find a matching DBLayer!");
        }
    }

    @Override
    public void add(Polygon3D element) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void remove(Polygon3D element) {
        throw new UnsupportedOperationException();
    }

    @SuppressWarnings("unchecked")
    public void calculateVisibleElements(Envelope envelope, int zoom) {
        if (dbLayer == null) {
            return;
        }

        if (zoom < minZoom) {
            setVisibleElements(null);
            return;
        }

        // TODO: use fromInternal(Envelope) here
        MapPos bottomLeft = projection.fromInternal(envelope.getMinX(), envelope.getMinY());
        MapPos topRight = projection.fromInternal(envelope.getMaxX(), envelope.getMaxY());

        List<Geometry> visibleElementslist = spatialLite.qrySpatiaLiteGeom(new Envelope(bottomLeft.x, topRight.x,
                bottomLeft.y, topRight.y), maxObjects, dbLayer, new String[] { heightColumnName }, 0, 0);

        long start = System.currentTimeMillis();
        List<Polygon3D> newVisibleElementsList = new LinkedList<Polygon3D>();
        for (Geometry geometry : visibleElementslist) {

            float height;
            if (heightColumnName == null) {
                height = DEFAULT_HEIGHT;
            } else {
                height = Float.parseFloat(((Map<String, String>) geometry.userData).get(heightColumnName)) * heightFactor;
            }
            Polygon3D polygon3D = new Polygon3D(((Polygon) geometry).getVertexList(), ((Polygon) geometry).getHolePolygonList(), height, null, styleSet, null);
            polygon3D.attachToLayer(this);
            polygon3D.setActiveStyle(zoom);
            newVisibleElementsList.add(polygon3D);
        }
        Log.debug("Triangulation time: " + (System.currentTimeMillis() - start));

        setVisibleElements(newVisibleElementsList);
    }
}
