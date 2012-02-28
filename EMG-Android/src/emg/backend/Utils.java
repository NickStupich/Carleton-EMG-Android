package emg.backend;
public class Utils {
	public static int getNumChannels(int channels)
	{
		int result = 0;
		for(int i=0;i<Constants.MAX_CHANNELS;i++)
		{
			if((channels & 1<<i) > 0)
				result ++;
		}
		return result;
	}
}
