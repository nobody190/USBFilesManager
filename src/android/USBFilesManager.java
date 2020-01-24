package cordova.plugin.usbfilesmanager;

import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.app.Activity;
import android.support.v4.provider.DocumentFile;
import android.content.Context;

import java.lang.Exception;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileInputStream;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.File;
import java.util.ArrayList;

public class USBFilesManager extends CordovaPlugin {
    private static final String ACTION_SAVE_FILE_TO_USB = "saveFileToUSB";
    private static final String ACTION_GET_FILES_FROM_USB = "getFilesFromUSB";
    private static final String ACTION_GET_FILES_FROM_USB_BY_URI = "getFilesFromUSBByUri";
    private static final String ACTION_COPY_FILE_FROM_USB = "copyFileFromUSB";
    private static final String ACTION_DELETE_FILE_FROM_USB = "deleteFileFromUSB";
    private String inputFileName = null;

    private CallbackContext callback;

    private static final int PICK_FOLDER_REQUEST_FOR_SAVE = 1;
    private static final int PICK_FOLDER_REQUEST_FOR_GET = 2;

    @Override
    public boolean execute(
            String action,
            JSONArray args,
            CallbackContext callbackContext
    ) {
        try {
            if (action.equals(USBFilesManager.ACTION_SAVE_FILE_TO_USB)) {
                this.saveFileToTargetDirectory(callbackContext, args.getString(0));
                return true;
            } else if (action.equals(USBFilesManager.ACTION_GET_FILES_FROM_USB)) {
                this.getFilesFromTargetDirectory(callbackContext);
                return true;
            } else if (action.equals(USBFilesManager.ACTION_GET_FILES_FROM_USB_BY_URI)) {
                this.getFilesListByUri(callbackContext, Uri.parse(args.getString(0)));
                return true;
            } else if (action.equals(USBFilesManager.ACTION_COPY_FILE_FROM_USB)) {
                this.copyFileFromUSB(callbackContext, args.getString(0), args.getString(1));
                return true;
            } else if (action.equals(USBFilesManager.ACTION_DELETE_FILE_FROM_USB)) {
                this.deleteFileFromUSB(callbackContext, args.getString(0), args.getString(1));
                return true;
            }
        } catch (JSONException err) {
            this.callback.error("Execute failed: " + err.toString());
        }

        return false;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == USBFilesManager.PICK_FOLDER_REQUEST_FOR_SAVE && this.callback != null) {
            if (resultCode == Activity.RESULT_OK) {
                try {
                    JSONObject result = new JSONObject();
                    Uri uri = data.getData();

                    String errorCopy = copyFile(this.inputFileName, uri);

                    result.put("error", errorCopy);
                    result.put("uri", uri);

                    this.callback.success(result);
                } catch (Exception err) {
                    this.callback.error("Failed to copy file: " + err.toString());
                }
            } else {
                this.callback.error("Folder URI was null.");
            }

        } else if (requestCode == USBFilesManager.PICK_FOLDER_REQUEST_FOR_GET && this.callback != null) {
            if (resultCode == Activity.RESULT_OK) {
                try {
                    JSONObject result = new JSONObject();
                    Uri uri = data.getData();

                    getFilesListByUri(this.callback, uri);
                } catch (Exception err) {
                    this.callback.error("Failed to copy file: " + err.toString());
                }
            } else {
                this.callback.error("Folder URI was null.");
            }

        } else if (resultCode == Activity.RESULT_CANCELED) {
            this.callback.success("RESULT_CANCELED");
        } else {
            this.callback.error(resultCode);
        }
    }

    private void saveFileToTargetDirectory(CallbackContext callbackContext, String fileName) {
        this.inputFileName = fileName;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        Intent chooser = Intent.createChooser(intent, "Open folder");
        cordova.startActivityForResult(this, chooser, USBFilesManager.PICK_FOLDER_REQUEST_FOR_SAVE);

        PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
        pluginResult.setKeepCallback(true);
        this.callback = callbackContext;
        callbackContext.sendPluginResult(pluginResult);
    }

    private void getFilesFromTargetDirectory(CallbackContext callbackContext) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        Intent chooser = Intent.createChooser(intent, "Open folder");
        cordova.startActivityForResult(this, chooser, USBFilesManager.PICK_FOLDER_REQUEST_FOR_GET);

        PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
        pluginResult.setKeepCallback(true);
        this.callback = callbackContext;
        callbackContext.sendPluginResult(pluginResult);
    }

    private void getFilesListByUri(CallbackContext callbackContext, Uri uri) {
        try {
            JSONObject result = new JSONObject();
            DocumentFile filesDir = DocumentFile.fromTreeUri(cordova.getActivity(), uri);
            DocumentFile[] documents = filesDir.listFiles();

            for (final DocumentFile file : documents) {
                JSONObject resultFile = new JSONObject();
                if (file.isFile()) {
                    resultFile.put("isFile", file.isFile());
                    resultFile.put("name", file.getName());
                    resultFile.put("url", file.getUri());
                    resultFile.put("type", file.getType());
                    result.put(file.getName(), resultFile);
                }
            }
            resultFile.put("test", "TEST");
            callbackContext.success(result);
        } catch (Exception err) {
            callbackContext.error("Failed to read file: " + err.toString());
        }
    }

    private static String getFileMimeType(String fileName) {
        String mimeType = "application/" + fileName.substring(fileName.lastIndexOf(".") + 1, fileName.length());
        return mimeType;
    }

    private void copyFileFromUSB(CallbackContext callbackContext, String fileUri, String fileName) {
        InputStream in = null;
        OutputStream out = null;
        String error = null;
        String targetPath = cordova.getActivity().getApplicationContext().getExternalFilesDir(null).getAbsolutePath() + "/" + fileName;

        try {
            JSONObject result = new JSONObject();


            try {
                in = cordova.getActivity().getContentResolver().openInputStream(Uri.parse(fileUri));
                out = cordova.getActivity().getContentResolver().openOutputStream(Uri.fromFile(new File(targetPath)));

                byte[] buffer = new byte[1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                in.close();
                out.flush();
                out.close();


            } catch (FileNotFoundException fnfe1) {
                error = fnfe1.getMessage();
            } catch (Exception e) {
                error = e.getMessage();
            }
            result.put("error", error);
            result.put("fileName", fileName);
            result.put("fileUri", fileUri);
            result.put("url", targetPath);
            callbackContext.success(result);
        } catch (Exception err) {
            callbackContext.error("Failed to move file: " + err.toString());
        }
    }

    private void deleteFileFromUSB(CallbackContext callbackContext, String fileUri, String fileName) {
//        InputStream in = null;
//        OutputStream out = null;
//        String error = null;
//
//        String targetPath = cordova.getActivity().getApplicationContext().getExternalFilesDir(null).getAbsolutePath() + "/" + fileName;

        try {
            JSONObject result = new JSONObject();


//            try {
//                in = cordova.getActivity().getContentResolver().openInputStream(Uri.parse(fileUri));
//                out = cordova.getActivity().getContentResolver().openOutputStream(Uri.fromFile(new File(targetPath)));
//
//                byte[] buffer = new byte[1024];
//                int read;
//                while ((read = in.read(buffer)) != -1) {
//                    out.write(buffer, 0, read);
//                }
//                in.close();
//                out.flush();
//                out.close();
//
//
//            } catch (FileNotFoundException fnfe1) {
//                error = fnfe1.getMessage();
//            } catch (Exception e) {
//                error = e.getMessage();
//            }
//            result.put("error", error);
//            result.put("fileName", fileName);
//            result.put("fileUri", fileUri);
//            result.put("url", targetPath);
            callbackContext.success(result);
        } catch (Exception err) {
            callbackContext.error("Failed to remove file: " + err.toString());
        }
    }

    private String copyFile(String inputFile, Uri treeUri) {
        String inputPath = cordova.getActivity().getApplicationContext().getExternalFilesDir(null).getAbsolutePath();
        InputStream in = null;
        OutputStream out = null;
        String error = null;
        DocumentFile pickedDir = DocumentFile.fromTreeUri(cordova.getActivity(), treeUri);
        String mimeType = getFileMimeType(inputFile);

        try {
            DocumentFile newFile = pickedDir.createFile(mimeType, inputFile);
            out = cordova.getActivity().getContentResolver().openOutputStream(newFile.getUri());
            in = new FileInputStream(inputPath + "/" + inputFile);

            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            in.close();
            out.flush();
            out.close();

        } catch (FileNotFoundException fnfe1) {
            error = fnfe1.getMessage();
        } catch (Exception e) {
            error = e.getMessage();
        }

        return error;
    }
}