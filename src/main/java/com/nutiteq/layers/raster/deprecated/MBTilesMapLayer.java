package com.nutiteq.layers.raster.deprecated;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import android.content.Context;

import com.nutiteq.components.Components;
import com.nutiteq.components.MapTile;
import com.nutiteq.components.MutableMapPos;
import com.nutiteq.db.MBTilesDbHelper;
import com.nutiteq.log.Log;
import com.nutiteq.projections.Projection;
import com.nutiteq.rasterlayers.RasterLayer;
import com.nutiteq.tasks.FetchTileTask;

/**
 * A raster layer class for reading map tiles from local Sqlite database.
 * The database must contain table <i>tiles</i> with the following fields:
 * <p>
 * <p>
 * <i>zoom_level</i> (zoom level of the tile), <i>tile_column</i>,
 * <i>tile_row</i>, <i>tile_data</i> (compressed tile bitmap)
 */
@Deprecated
public class MBTilesMapLayer extends RasterLayer implements UtfGridLayerInterface{

    private MBTilesDbHelper db;
    private boolean tmsY;
    private boolean hasUtfGrid = false;

    private class DbFetchTileTask extends FetchTileTask {

        private MBTilesDbHelper db;
        private int z;
        private int x;
        private int y;

        public DbFetchTileTask(MapTile tile, Components components, long tileIdOffset, MBTilesDbHelper db) {
            super(tile, components, tileIdOffset);
            this.db = db;
            this.z = tile.zoom;
            this.x = tile.x;
            this.y = tile.y;
        }

        @Override
        public void run() {
            super.run();
            Log.debug("DbMapLayer task: Start loading " + " zoom=" + z + " x=" + x + " y=" + y);

            // y is flipped (origin=sw in mbtiles)
            finished(db.getTileImg(z, x, (1 << (z)) - 1 - y));
            cleanUp();
        }

    }


    /**
     * Default constructor.
     * 
     * @param projection
     *            projection for the layer.
     * @param minZoom
     *            minimum zoom supported by the layer.
     * @param maxZoom
     *            maximum zoom supported by the layer.
     * @param id
     *            unique layer id. Id for the layer must be depend ONLY on the
     *            layer source, otherwise tile caching will not work properly.
     * @param path
     *            path to the local Sqlite database file. SQLiteException will
     *            be thrown if the database does not exist
     *            or can not be opened in read-only mode.
     * @param ctx
     *            Android application context.
     * @throws IOException
     *             exception if file not exists
     */
    public MBTilesMapLayer(Projection projection, int minZoom, int maxZoom,
            int id, String path, Context ctx) throws IOException {
        super(projection, minZoom, maxZoom, id, path);

        if (!(new File(path)).exists()) {
            throw new IOException("not existing file: " + path);
        }

        db = new MBTilesDbHelper(ctx, path);
        db.open();

        hasUtfGrid = db.getMetadata().containsKey("template");

    }

    @Override
    public void fetchTile(MapTile tile) {
        if (db == null) {
            return;
        }
        if (tile.zoom < minZoom || tile.zoom > maxZoom) {
            return;
        }

        MapTile flippedTile;
        if (!tmsY) {
            flippedTile = new MapTile(tile.x, tile.y, tile.zoom, tile.id);
        } else {
            // flip Y coordinate for standard TMS
            flippedTile = new MapTile(tile.x, (1 << (tile.zoom)) - 1 - tile.y,
                    tile.zoom, tile.id);
        }

        Log.debug("DbMapLayer: Start loading " + " zoom=" + flippedTile.zoom
                + " x=" + flippedTile.x + " y=" + flippedTile.y);
        executeFetchTask(new DbFetchTileTask(flippedTile, components,
                tileIdOffset, db));
    }

    @Override
    public void flush() {
    }

    /**
     * Close database file. After calling this, new tiles will not be read from
     * the database.
     */
    public void close() {
        db.close();
        db = null;
    }

    public void setTmsY(boolean tmsY) {
        this.tmsY = tmsY;
    }

    public MBTilesDbHelper getDatabase() {
        return this.db;
    }

    @Override
    public Map<String, String> getUtfGridTooltips(MapTile clickedTile, MutableMapPos tilePos, String template) {
        // template will be loaded in database request, ignored here
        return db.getUtfGridTooltips(clickedTile, tilePos);
    }

    @Override
    public boolean hasUtfGridTooltips() {
        return hasUtfGrid;
    }
}
