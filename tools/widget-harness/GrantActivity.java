package io.betterlyrics.widgettest;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;

/** Grants the disposable test tree in the same way as a document picker result. */
public final class GrantActivity extends Activity {
    @Override public void onCreate(Bundle state){
        super.onCreate(state);Uri tree=DocumentsContract.buildTreeDocumentUri("io.betterlyrics.widgettest.documents","root");Uri file=DocumentsContract.buildDocumentUriUsingTree(tree,"playlist");
        ClipData clip=ClipData.newRawUri("fixture",file);clip.addItem(new ClipData.Item(tree));
        Intent target=new Intent().setClassName("io.betterlyrics.mobile","io.betterlyrics.mobile.MainActivity").setData(file)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        target.setClipData(clip);
        startActivity(target);finish();
    }
}
