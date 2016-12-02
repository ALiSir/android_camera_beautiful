package beautiful.libo.com.android_camera_beautiful;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Toast;

import beautiful.libo.com.android_camera_beautiful.beautifulutil.BeautifulMain;

public class MainActivity extends Activity {
    private SeekBar sb;
    private ImageView iv;
    private Bitmap bt_old;
    private Bitmap bt_new;
    private BeautifulMain bm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initView();
        initRead();

    }

    void initRead(){
        bt_old = BitmapFactory.decodeResource(getResources(),R.mipmap.android_beautiful);
        bm = new BeautifulMain();
    }

    void initView(){
        sb = (SeekBar) findViewById(R.id.seekBar);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                bt_new = bm.beautifyImg(bt_old,seekBar.getProgress()/5);
                iv.setImageBitmap(bt_new);
            }
        });

        iv = (ImageView) findViewById(R.id.imageView);
        iv.setImageResource(R.mipmap.android_beautiful);
    }

}
