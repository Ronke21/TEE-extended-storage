package XFlashStorage;

import com.intel.langutil.ArrayUtils;
import com.intel.util.DebugPrint;

public class XFlashStorageSample extends XFlashStorageApplet {

	public int getFileNumber() {
		return 0;
	}

	protected int onInitFunction(byte[] request) {
		return APPLET_SUCCESS;
	}

	protected AppletResponseModel onInvokeFunction(int commandId, byte[] request) {

		DebugPrint.printString("Received command Id: " + commandId + ".");
		DebugPrint.printString(request.length == 0 ? "no payload" : "payload");
		DebugPrint.printBuffer(request);
		AppletResponseModel responseModel = new AppletResponseModel();
		responseModel.ResponseCode = commandId;

		byte[] buffer = new byte[256];
		if(request.length > 0)
		{
			try {
				DebugPrint.printString("Before write");
				xStorage.writeFlashData(commandId, request, 0, request.length);
			} catch (Exception e) {
				DebugPrint.printString(e.toString());
			}
		}
		else {
			try {
				DebugPrint.printString("Before read");
				if(xStorage.getMaxFileName() > commandId) {
					int size = xStorage.readFlashData(commandId, buffer, 0);
					responseModel.ResponseData = new byte[size];
					ArrayUtils.copyByteArray(buffer, 0, responseModel.ResponseData, 0, responseModel.ResponseData.length);
				}
				else {
					responseModel.ResponseData = new byte[]{'F','i','l','e',' ', 'n','o', 't', ' ', 'e', 'x','i', 's', 't', 's'};
				}
			} catch (Exception e) {
				DebugPrint.printString(e.toString());
			}
		}
		return responseModel;
	}

	protected int onCloseFunction() {
		return APPLET_SUCCESS;
	}

}
