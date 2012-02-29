package emg.bluetooth.test;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.widget.TextView;
import emg.backend.*;

public class AndroidBluetoothTestActivity extends Activity implements ITransformListener {
	
	String DEVICE_ADDRESS = "00:06:66:04:9B:21";
	byte channels = 1;//each bit is a channel (bits1-6)
	
	TextView mainText;
	DataProtocol dataProtocol;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        mainText = (TextView) findViewById(R.id.main_text); 
        
        this.dataProtocol = new DataProtocol(this, this, channels, DEVICE_ADDRESS);
        this.dataProtocol.Start();
    }
    
    /* Really good idea to stop the bluetooth connection(tell microcontroller to stop, then disconnect the connection here*/
    @Override
    public void onDestroy()
    {
    	this.dataProtocol.StopAndDisconnect();
    }

	public void addData(int[] data) {
		Message msg = new Message();
		msg.obj = data;
		this.newDataHandler.sendMessage(msg);
	}
	
	Handler newDataHandler = new Handler(){
		@Override
		public void handleMessage(Message msg){
			int[] data = (int[])msg.obj;
			
			String s = "";
			for(int i=0;i<data.length;i++)
				s += Integer.toString(data[i]) + " ";
			
			mainText.setText(s);
		}
	};
}