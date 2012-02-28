package emg.backend;
import android.content.Context;
import android.util.Log;


public class DataProtocol implements IBluetoothDataListener {

	ITransformListener transformListener;
	
	private String TAG = "DataProtocol";
	
	private static int CONTROL_BYTE = 255;	//deliminates chunks of data
	
	private enum ExpectedNext{
		CONTROL,
		GAIN,
		DATA
	};
	
	private byte[] gains;
	private byte[][] data;
	
	private int numChannels;
	private int channelIndex, dataIndex;
	private ExpectedNext expected;
	
	private Bluetooth bluetooth;
	private String address;
	private byte channels;
	
	public DataProtocol(Context context, ITransformListener transformListener, byte channels, String address)
	{
		this.channels = channels;
		this.transformListener = transformListener;
		this.numChannels = Utils.getNumChannels(channels);
		this.gains = new byte[this.numChannels];
		this.data = new byte[this.numChannels][Constants.NUM_FOURIER_BINS];
		
		this.channelIndex = 0;
		this.dataIndex = 0;
		this.expected = ExpectedNext.CONTROL;
		
		try {
			bluetooth = new Bluetooth(context, this);
		} catch (Exception e) {
			Log.e(TAG, e.toString());
		}
		
		this.address = address;
	}
	
	/*
	 * Starts up the bluetooth connection, and waits for the ACK bit before returning whether it makes sense
	 */
	public boolean Start()
	{
		this.bluetooth.Connect(this.address);
		this.bluetooth.sendByte((byte)((1<<7) & this.channels));
		byte[] buffer = this.bluetooth.readDirectly(1, 1000);
		if(buffer == null)
		{
			Log.e(TAG, "Buffer is null");
			return false;
		}
		if(buffer[0] == this.channels)
		{
			this.bluetooth.startReading();
			return true;
		}
		else
		{
			Log.e(TAG, "Failed to start - received " + Byte.toString(buffer[0]) + " when " + Byte.toString(this.channels) + " was expected");
			this.Stop();
			return false;
		}
		
	}
	
	public void Stop()
	{
		this.bluetooth.sendByte((byte)0);
		this.bluetooth.Disconnect();
	}
	
	/* multiple gains by data, and return everything as one big happy array
	 */
	public int[] getDataArray()
	{
		int[] result = new int[this.numChannels * Constants.NUM_FOURIER_BINS];
		int x =0;
		for(int i=0;i<this.numChannels;i++)
		{
			for(int j=0;j< Constants.NUM_FOURIER_BINS;j++)
			{
				result[x] = this.data[i][j] * this.gains[i];
				x++;
			}
		}
		return result;
	}
	
	public void addByte(byte b) {
		switch(this.expected)
		{
		case CONTROL:
			if(b != CONTROL_BYTE)
			{
				Log.e(TAG, "Expected Control byte, instead received " + Byte.toString(b));
			}
			if(this.channelIndex > 0)	//true if it's not the first loop
			{
				this.transformListener.addData(this.getDataArray());
			}
			
			//reset everything for the next batch of data
			this.channelIndex = 0;
			this.dataIndex = 0;
			this.expected = ExpectedNext.GAIN;
			
		case GAIN:
			if(b == CONTROL_BYTE)
			{
				Log.e(TAG, "Expected gain, received control byte for channel " + Integer.toString(this.channelIndex));
			}
			this.gains[this.channelIndex] = b;
			this.expected = ExpectedNext.DATA;
			
		case DATA:
			if(b == CONTROL_BYTE)
			{
				Log.e(TAG, "Expected Data, received control byte for channel" + Integer.toString(this.channelIndex));
			}
			this.data[this.channelIndex][this.dataIndex] = b;
			this.dataIndex++;
			
			if(this.dataIndex == Constants.NUM_FOURIER_BINS)
			{
				this.dataIndex = 0;
				this.channelIndex++;
				if(this.channelIndex == this.numChannels)
				{
					this.expected = ExpectedNext.CONTROL;
				}
				else 
				{
					this.expected = ExpectedNext.GAIN;
				}
			}
		}
	}

}
