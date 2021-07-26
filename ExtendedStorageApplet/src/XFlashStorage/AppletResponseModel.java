package XFlashStorage;

/***
 * A class that represent a response from Applets that are implemented via XFlashStorageApplet
 * @author Shai Falach and Ron Aharon Keinan
 */
public class AppletResponseModel {
	
	/***
	 * The Status code of the Applet I.E: APPLET_SUCCESS.
	 */
	public int StatusCode =0;
	/***
	 * The response Code we want to send back (will be used inside setResponseCode).
	 */
	public int ResponseCode;
	/***
	 * The Response Data we need to send back to the Host (will be used inside setResponse).
	 */
	public byte[] ResponseData = {'O', 'K'};
}
