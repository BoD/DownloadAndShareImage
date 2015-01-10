/*
 * This source is part of the
 *      _____  ___   ____
 *  __ / / _ \/ _ | / __/___  _______ _
 * / // / , _/ __ |/ _/_/ _ \/ __/ _ `/
 * \___/_/|_/_/ |_/_/ (_)___/_/  \_, /
 *                              /___/
 * repository.
 *
 * Copyright (C) 2015 Benoit 'BoD' Lubek (BoD@JRAF.org)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.jraf.android.downloadandshareimage.app.downloadandshare;

import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.FragmentActivity;
import android.util.Patterns;
import android.widget.Toast;

import org.jraf.android.downloadandshareimage.R;
import org.jraf.android.util.async.Task;
import org.jraf.android.util.async.TaskFragment;
import org.jraf.android.util.file.FileUtil;
import org.jraf.android.util.http.HttpUtil;
import org.jraf.android.util.log.wrapper.Log;

import java.io.File;
import java.io.IOException;

public class DownloadAndShareActivity extends FragmentActivity {
    private String mUrl;

    private static enum Error {
        DOWNLOAD,
        NOT_AN_IMAGE,
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mUrl = getIntent().getStringExtra(Intent.EXTRA_TEXT);
        Log.d("mUrl=" + mUrl);
        // Check for valid URL
        boolean isUrl = mUrl != null && Patterns.WEB_URL.matcher(mUrl).matches();
        if (!isUrl) {
            Log.w("invalid valid URL " + mUrl);
            Toast.makeText(this, R.string.error_invalidUrl, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        startDownload();
    }

    private void startDownload() {
        new TaskFragment(new Task<DownloadAndShareActivity>() {
            public File mFile;
            public Error mError;

            @Override
            protected void doInBackground() throws Throwable {
                DownloadAndShareActivity a = getActivity();
                try {
                    mFile = getTemporaryFile();

                    // Start downloading now (blocking)
                    HttpUtil.get(a.mUrl).receive(mFile);

                    // Check for a valid image
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(mFile.getPath(), opts);
                    Log.d("width=" + opts.outWidth + " height=" + opts.outHeight);
                    if (opts.outWidth == -1) {
                        mFile = null;
                        mError = Error.NOT_AN_IMAGE;
                    }
                } catch (IOException e) {
                    Log.w("Could not download " + mUrl, e);
                    mFile = null;
                    mError = Error.DOWNLOAD;
                }
            }

            @Override
            protected void onPostExecuteOk() {
                DownloadAndShareActivity a = getActivity();
                if (mFile == null) {
                    switch (mError) {
                        case DOWNLOAD:
                            Toast.makeText(a, R.string.error_download, Toast.LENGTH_LONG).show();
                            break;

                        case NOT_AN_IMAGE:
                            Toast.makeText(a, R.string.error_notAnImage, Toast.LENGTH_LONG).show();
                            break;
                    }

                    a.finish();
                    return;
                }

                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(mFile));
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                // Here we hardcode to jpeg, which is OK because other apps will filter on "image/*"
                intent.setType("image/jpeg");

                a.startActivity(Intent.createChooser(intent, null));
                a.finish();
            }
        }.toastFail(R.string.error_download)).execute(getSupportFragmentManager());
    }

    public File getTemporaryFile() throws IOException {
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        dir.mkdirs();
        String fileName = FileUtil.getValidFileName(mUrl);
        fileName = fileName.substring(0, Math.min(80, fileName.length()));
        File file = new File(dir, fileName);
        Log.d("file=" + file);
        if (file.exists()) file.delete();
        return file;
    }
}
