package XFlashStorage;

import com.intel.util.*;

/***
 * Abstraction Class for using our Extended Storage module.
 *  @author Shai Falach and Ron Aharon Keinan
 *
 */
public abstract class XFlashStorageApplet extends IntelApplet {
	protected XFlashStorage xStorage;

	/***
	 * A function that user need to give back a file number where to save the IV of the AES
	 * @return the file number in the flash storage to save the IV into.
	 */
	protected abstract int getFileNumber();
	/***
	 * A function that will be called onInit with the original Init buffer of the user.
	 * @param request - A byte array that may or may not include DB from HD.
	 * @return - Applet Status code
	 */
	protected abstract int onInitFunction(byte[] request);
	/***
	 * A function that will be called on Invoke Command
	 * and will contain the main Logic of the Applet that inherits this class.
	 * @param commandId - The command id the the host sent to us.
	 * @param request - The Data that the host sent with this request.
	 * @return - Applet Response object.
	 */
	protected abstract AppletResponseModel onInvokeFunction(int commandId, byte[] request);
	/***
	 * A function that will be called onClose function of the applet.
	 * @return - Applet status code
	 */
	protected abstract int onCloseFunction();

	public int onInit(byte[] request) {
		xStorage = XFlashStorage.createInstance(request,getFileNumber());
		int statusCode = onInitFunction(request);
		return statusCode;
	}

	public int invokeCommand(int commandId, byte[] request) {
		AppletResponseModel response = onInvokeFunction(commandId, request);
		
		response.ResponseData = xStorage.setResponse(response.ResponseData, 0, response.ResponseData.length);
		
		setResponseCode(response.ResponseCode);
		setResponse(response.ResponseData, 0, response.ResponseData.length);
		
		return response.StatusCode;
	}

	public int onClose() {
		int statusCode = onCloseFunction();
		return statusCode;
	}
}
