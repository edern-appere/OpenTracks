/*
 * Copyright 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package de.dennisguse.opentracks.io.file.exporter;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import de.dennisguse.opentracks.content.ContentProviderUtils;
import de.dennisguse.opentracks.content.Track;
import de.dennisguse.opentracks.content.Waypoint;
import de.dennisguse.opentracks.util.FileUtils;

/**
 * KMZ track exporter.
 *
 * @author Jimmy Shih
 */
public class KmzTrackExporter implements TrackExporter {
    public static final String KMZ_IMAGES_DIR = "images";
    public static final String KMZ_KML_FILE = "doc.kml";

    private static final String TAG = KmzTrackExporter.class.getSimpleName();
    private static final int BUFFER_SIZE = 4096;

    private final ContentProviderUtils contentProviderUtils;
    private final TrackExporter fileTrackExporter;
    private final Track[] tracks;

    private boolean exportPhotos;

    /**
     * Constructor.
     *
     * @param contentProviderUtils the content provider utils
     * @param trackExporter    the file track exporter
     * @param tracks               the tracks to export
     */
    public KmzTrackExporter(ContentProviderUtils contentProviderUtils, TrackExporter trackExporter, Track[] tracks, boolean exportPhotos) {
        this.contentProviderUtils = contentProviderUtils;
        this.fileTrackExporter = trackExporter;
        this.tracks = tracks;
        this.exportPhotos = exportPhotos;
    }

    @Override
    public boolean writeTrack(@NonNull Context context, @NonNull OutputStream outputStream) {
        ZipOutputStream zipOutputStream = null;
        try {
            zipOutputStream = new ZipOutputStream(outputStream);

            // Add kml file
            ZipEntry zipEntry = new ZipEntry(KMZ_KML_FILE);
            zipOutputStream.putNextEntry(zipEntry);

            boolean success = fileTrackExporter.writeTrack(context, zipOutputStream);
            zipOutputStream.closeEntry();
            if (!success) {
                Log.e(TAG, "Unable to write kml in kmz");
                return false;
            }

            // Add photos
            if (exportPhotos) addImages(context, zipOutputStream);
            return true;
        } catch (InterruptedException | IOException e) {
            Log.e(TAG, "Unable to write track", e);
            return false;
        } finally {
            if (zipOutputStream != null) {
                try {
                    zipOutputStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "Unable to close zip input stream", e);
                }
            }
        }
    }

    private void addImages(Context context, ZipOutputStream zipOutputStream) throws InterruptedException, IOException {
        for (Track track : tracks) {
            try (Cursor cursor = contentProviderUtils.getWaypointCursor(track.getId(), -1L, -1)) {
                if (cursor != null && cursor.moveToFirst()) {
                    for (int i = 0; i < cursor.getCount(); i++) {
                        if (Thread.interrupted()) {
                            throw new InterruptedException();
                        }
                        Waypoint waypoint = contentProviderUtils.createWaypoint(cursor);
                        if (waypoint.hasPhoto()) {
                            addImage(context, zipOutputStream, waypoint.getPhotoUrl());
                        }

                        cursor.moveToNext();
                    }
                }
            }
        }
    }

    private void addImage(Context context, ZipOutputStream zipOutputStream, String photoUrl) throws IOException {
        Uri uri = Uri.parse(photoUrl);

        try (InputStream inputStream = context.getContentResolver().openInputStream(uri)) {
            ZipEntry zipEntry = new ZipEntry(KMZ_IMAGES_DIR + File.separatorChar + FileUtils.sanitizeFileName(uri.getLastPathSegment()));
            zipOutputStream.putNextEntry(zipEntry);

            if (inputStream == null) throw new FileNotFoundException();

            readToOutputStream(inputStream, zipOutputStream);
            zipOutputStream.closeEntry();

            Log.i(TAG, "added an image to zip");
        } catch (FileNotFoundException e) {
            Log.e(TAG, "could not get image via FileProvider via uri " + uri);
        }
    }

    private void readToOutputStream(InputStream inputStream, OutputStream outputStream) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int byteCount;
        while ((byteCount = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, byteCount);
        }
    }
}