package XFlashStorage;

import com.intel.crypto.Random;
import com.intel.crypto.SymmetricBlockCipherAlg;
import com.intel.langutil.ArrayUtils;
import com.intel.util.FlashStorage;

/***
 *  A Class to extend Flash storage of applet using AES-256-CBC (PD),
 *  and external HD on the Host OS.
 * @author Shai Falach and Ron Aharon Keinan
 *
 */
public class XFlashStorage {
	public final static byte DBDirtyFlag = 1;

	public final static short HeaderSize = 2;
	public final static short BufferHead = 0;
	public final static short DataSizeHeader = 2;
	public final static short DataCountHeader = 4;
	public final static short IndexOffset = 6;
	
	public final static short MAX_BUFFER_SIZE = (short) 16384; 

	// static helper functions.
	/**
	 * A helper function gets byte array and give back short based on the first 2 bytes in the array.
	 * @param bytes
	 * @return
	 */
	public static short shortFromByteArray(byte[] bytes) {
		return (short) (((bytes[1] & 0xFF) << 8) | ((bytes[0] & 0xFF) << 0));
	}

	/**
	 * A helper function gets short value and convert it into bytes representation of that number;
	 * @param value - The short number to convert.
	 * @return the short number as bytes.
	 */
	public static byte[] shortToByteArray(short value) {
		return new byte[] { (byte) value, (byte) (value >> 8), };
	}
	/**
	 * A helper function that gets a onInit request byte array data and create a new XFlashStorage instance.
	 * @param request - The onInit request binary data.
	 * @param fileNum - the file index for RSA data location.
	 * @return A new XFlashStorage instance.
	 */
	public static XFlashStorage createInstance(byte[] request,int fileNum) {
		byte[] index;
		byte[] data;
		short fileCount=0;
		short indexSize=0;
		short dataSize=0;
		// we sent DB with initBuffer of the Host original install request. 
		// gets index and data from request buffer
		byte[] buffer = new byte[request.length-1];
		ArrayUtils.copyByteArray(request, 1, buffer, BufferHead, buffer.length);
		if(request.length > 0 && request[0] == DBDirtyFlag) {
			// get count
			byte[] countData = new byte[2];
			ArrayUtils.copyByteArray(buffer, DataCountHeader, countData, BufferHead, HeaderSize);
			fileCount = shortFromByteArray(countData);

			// get index
			indexSize = shortFromByteArray(buffer);
			index = new byte [indexSize];
			ArrayUtils.copyByteArray(buffer, IndexOffset, index, BufferHead, indexSize);

			// get data
			byte[] dataLength = new byte[2];
			ArrayUtils.copyByteArray(buffer, DataSizeHeader, dataLength, BufferHead, HeaderSize);
			dataSize = shortFromByteArray(dataLength);
			data = new byte [dataSize];
			ArrayUtils.copyByteArray(buffer, IndexOffset+indexSize, data, BufferHead, dataSize);


			// return the original initBuffer to user applet.
			int initOffset = IndexOffset+dataSize+indexSize;
			int initBufferlength = buffer.length-initOffset;
			request = new byte[initBufferlength];
			ArrayUtils.copyByteArray(buffer, initOffset, request, BufferHead, initBufferlength);
		}
		else {
			index = new byte[0];
			data = new byte[0];
			request = new byte[buffer.length];
			ArrayUtils.copyByteArray(buffer, BufferHead, request, BufferHead, request.length);
		}
		return new XFlashStorage(index,data,fileNum,fileCount,indexSize,dataSize);
	}

	// private fields
	private short dbCount;
	private byte[] dbData;
	private short[] dbIndex;
	private SymmetricBlockCipherAlg crypto;
	private boolean dirty = false;

	/**
	 * A simple constructor for extended flash storage, should be called in onInit of an applet.
	 * @param index - the index data of the files DB or zero-length empty byte array
	 * @param data - the DB data of the files or zero-length empty byte array
	 * @param fileNum - where the applet want the RSA keys to be saved/read from.
	 */
	protected XFlashStorage(byte[] index, byte[] data,int fileNum,short fileCount, short indexSize, short dataSize) {
		setupAES(fileNum);
		setupDB(fileCount,indexSize,dataSize,index, data);
	}

	/**
	 * A helper function thats read data from flash storage of applet.
	 * @param fileNumber - The file number that we want to read.
	 * @return The file Data.
	 */
	private byte[] readFileFromStorage(int fileNumber) {
		byte[] fileData = new byte[FlashStorage.getFlashDataSize(fileNumber)];
		FlashStorage.readFlashData(fileNumber, fileData, BufferHead);
		return fileData;
	}

	/***
	 * A function to setup AES IV and save it to Flash Storage.
	 * @param fileNum
	 */
	private void setupAES(int fileNum) {
		// File number is not allowed to be less than 0.
		if(fileNum < 0) {
			throw new ExceptionInInitializerError("Can not have a negative file number");
		}
		crypto = SymmetricBlockCipherAlg.create(SymmetricBlockCipherAlg.ALG_TYPE_PBIND_AES_256_CBC);
		// AES keys exists should pull from applet storage.		
		short blockSize = crypto.getBlockSize();
		if(fileNum < FlashStorage.getMaxFileName() && FlashStorage.getFlashDataSize(fileNum) > 0){

			byte[] fileData = new byte[blockSize];
			fileData = readFileFromStorage(fileNum);

			crypto.setIV(fileData, BufferHead, blockSize);
		}
		else {
			// no iv yet.
			byte[] buffer = new byte[blockSize];
			Random.getRandomBytes(buffer, BufferHead, blockSize);
			crypto.setIV(buffer, BufferHead, blockSize);

			FlashStorage.writeFlashData(fileNum, buffer, BufferHead, blockSize);
		}
	}

	/**
	 * A helper function that setup DB from data sent in the initBuffer request from Host.
	 * @param index - binary information about the DB.
	 * @param data - binary information of the DB.
	 */
	private void setupDB(short fileCount, short indexSize, short dataSize, byte[] index, byte[] data) {
		// setup DB
		boolean dbExists = fileCount!=0;
		if(dbExists)
		{
			dbCount = fileCount;
			// get index
			byte[] indexDecrypted = new byte[MAX_BUFFER_SIZE];
			decryptFiles(index,indexDecrypted);
			dbIndex = new short[dbCount];
			byte[] fileSize = new byte [2];
			for (int i = 0; i < dbCount; i++) {
				fileSize[0]= indexDecrypted[i*2];
				fileSize[1] = indexDecrypted[i*2+1];
				dbIndex[i] = shortFromByteArray(fileSize);
			}
			// get data
			byte[] dataDecrypted = new byte[4096]; 
			short dataLen = decryptFiles(data, dataDecrypted);
			dbData = new byte[dataLen];
			ArrayUtils.copyByteArray(dataDecrypted, BufferHead, dbData, BufferHead, dataLen);
		}
		else {
			dbData = new byte[0];
			dbIndex = new short[0];
			dbCount = 0;
		}
	}
	/**
	 * A helper function that checks if given index is valid in our DB.
	 * @param fileNum - the file index we want to access.
	 */
	private boolean validateFileIndex(int fileNum) {
		return (fileNum < dbCount && fileNum >= 0);
		//			throw new ArrayIndexOutOfBoundsException("Bad index given as an argument.");
	}
	/**
	 * A function that empty the data of file in storage.
	 * @param fileNum - the file number to delete.
	 * @throws Throwable - if the file number is negative or out of bounds.
	 */
	public void eraseFlashData(int fileNum) throws Throwable {
		boolean fileExists = validateFileIndex(fileNum);
		if(!fileExists) {
			throw new ArrayIndexOutOfBoundsException("Bad index given as an argument.");
		}
		int fileOffset = getFileOffset(fileNum);
		int fileEnd = fileOffset + getFlashDataSize(fileNum);
		for (int i = fileOffset; i < fileEnd; i++) {
			dbData[i] = 0;
		}
	}
	/**
	 * A function that gets the file byte size.
	 * @param fileNum - the file index
	 * @return - the file byte size.
	 * @throws Throwable - if the fileNum parameter was invalid.
	 */
	public int getFlashDataSize(int fileNum) throws Exception {
		boolean fileExists = validateFileIndex(fileNum);
		if(!fileExists) {
			return 0;
		}
		return dbIndex[fileNum];
	}
	/**
	 * A function that gets the total files in our DB.
	 * @return - The files count.
	 */
	public int getMaxFileName() {
		return dbCount;
	}
	/**
	 * A function that gets back the file Data from our DB.
	 * @param fileNum - The file index.
	 * @param dest - A buffer that will store the file content.
	 * @param destOff - Where to start to write the data of the file.
	 * @return number of bytes written to @param dest.
	 * @throws Throwable if file number is invalid. 
	 */
	public int readFlashData(int fileNum, byte[] dest, int destOff) throws Exception {
		boolean fileExists = validateFileIndex(fileNum);
		if(!fileExists) {
			throw new ArrayIndexOutOfBoundsException("Bad index given as an argument.");
		}
		int offset = getFileOffset(fileNum);
		short fileSize = dbIndex[fileNum];
		if(fileSize == 0){
			dest = new byte[0];
			return fileSize;
		}
		ArrayUtils.copyByteArray(dbData, offset, dest, destOff, fileSize);
		return fileSize;
	}
	/**
	 * A function to update DB file/ write a new file to DB.
	 * @param fileNum - where to store the file in the DB.
	 * @param src - The buffer that hold the file data.
	 * @param srcOff - where the file data starts in the buffer.
	 * @param srcLen - the file data length.
	 * @throws Throwable - if the file Number is invalid.
	 */
	public void writeFlashData(int fileNum, byte[] src, int srcOff, int srcLen) throws Exception {
		int dbDataLength = dbData.length;
		if(fileNum<0) {
			throw new ArrayIndexOutOfBoundsException("Bad index given as an argument.");
		}
		if(fileNum < dbCount) {
			// update DB data.
			int oldFileSize = getFlashDataSize(fileNum);
			byte[] newData = new byte[dbDataLength+(srcLen-oldFileSize)];

			// copy old data until the new file location
			int fileOffset = getFileOffset(fileNum);
			ArrayUtils.copyByteArray(dbData, BufferHead, newData, BufferHead, fileOffset);

			// copy the new data
			ArrayUtils.copyByteArray(src, srcOff, newData, fileOffset, srcLen);

			// copy the rest of old data
			ArrayUtils.copyByteArray(dbData, fileOffset+oldFileSize, newData, fileOffset+srcLen, dbDataLength-(fileOffset+oldFileSize));

			dbData = new byte[newData.length];

			ArrayUtils.copyByteArray(newData, BufferHead, dbData, BufferHead, dbDataLength);

			// update DB index.
			dbIndex[fileNum] = (short) srcLen;
		}
		else {
			// update dbCount
			dbCount = (short) (fileNum+1);
			// update DB index
			short[] newIndex = new short[dbCount];
			if(dbIndex.length>0) {
				ArrayUtils.copyShortArray(dbIndex, BufferHead, newIndex, BufferHead, dbIndex.length);				
			}
			newIndex[newIndex.length - 1] = (short) srcLen;
			dbIndex = new short[newIndex.length];
			ArrayUtils.copyShortArray(newIndex, BufferHead, dbIndex, BufferHead, newIndex.length);
			// update DB data
			byte[] newData = new byte[dbDataLength+srcLen];
			if(dbData.length >0) {
				ArrayUtils.copyByteArray(dbData, BufferHead, newData, BufferHead, dbDataLength);				
			}
			ArrayUtils.copyByteArray(src, srcOff, newData, dbDataLength, srcLen);
			dbData = new byte[newData.length];
			ArrayUtils.copyByteArray(newData, BufferHead, dbData, BufferHead, newData.length);
		}
		dirty  = true;
	}
	/**
	 * A helper function that decrypt the data that was arrived from host.
	 * @param input - The encrypted data buffer
	 * @param output - The buffer that will hold the decrypted data.
	 */
	private short decryptFiles(byte[] input, byte[] output) {
		return crypto.decryptComplete(input, (short) 0,(short) input.length, output, (short) 0);
	}
	/**
	 * A helper function that encrypt the data so we can send it to host to be saved on disk.
	 * @param input - The clear data we want to encrypt.
	 * @param output - The buffer that will hold the data after encryption.
	 * @throws Exception 
	 */
	private short encryptFiles(byte[] input, byte[] output) {
		short inputSize = (short) input.length;
		while(inputSize % 16 != 0){
			inputSize++;
		}
		byte[] dataWithPadding = new byte[inputSize];
		ArrayUtils.copyByteArray(input, BufferHead, dataWithPadding, BufferHead, input.length);

		return crypto.encryptComplete(dataWithPadding, BufferHead, inputSize, output, BufferHead);
	}
	/**
	 * A helper function that get where the file of index @param fileNum starts in our DB Blob.
	 * @param fileNum - the file index
	 * @return Where the file data starts. 
	 * @throws Throwable - if the @param fileNum is invalid.
	 */
	private int getFileOffset(int fileNum) throws Exception {
		boolean fileExists = validateFileIndex(fileNum);
		if(!fileExists) {
			throw new ArrayIndexOutOfBoundsException("Bad index given as an argument.");
		}
		int offset = 0;
		for(int i=0;i<fileNum; i++) {
			offset += dbIndex[i];
		}
		return offset;
	}
	/**
	 * A function that checks for changes in DB and then sent the updated DB if needed. 
	 * @param myResponse - The original response buffer
	 * @param index - The buffer offset index 
	 * @param length - The response Length.
	 * @throws Exception 
	 */
	public byte[] setResponse(byte[] myResponse, int index, int length) {
		if(dirty) {
			// copy index and files count
			byte[] indexData = new byte[2*dbIndex.length];
			byte[] fileSize = new byte[2];
			for (int i = 0; i < dbIndex.length; i++) {
				fileSize = shortToByteArray(dbIndex[i]);
				ArrayUtils.copyByteArray(fileSize, BufferHead, indexData, i*2, 2);
			}
			// set My Response to be the old myResponse + encrypted DB
			dirty=false;
			return encryptUpdatedDB(myResponse, indexData);
		}
		else {
			byte[] newResponse = new byte[myResponse.length+1];
			// put not dirty flag inside response
			newResponse[0]=0;
			ArrayUtils.copyByteArray(myResponse, BufferHead, newResponse, 1, myResponse.length);
			myResponse = new byte[newResponse.length];
			ArrayUtils.copyByteArray(newResponse, BufferHead, myResponse, BufferHead, myResponse.length);
			return myResponse;
		}
	}
	/**
	 * A helper function that setup our DB as one encrypted blob inside the response buffer.
	 * @param myResponse - The original buffer response.
	 * @param indexData - The converted to bytes index Data.
	 * @throws Exception 
	 */
	private byte[] encryptUpdatedDB(byte[] myResponse, byte[] indexData) {
		// encrypt dbData
		byte[] encryptedDB;
		byte[] buffer = new byte[MAX_BUFFER_SIZE]; // 8KB
		short dbSize = encryptFiles(dbData, buffer);
		encryptedDB = new byte[dbSize];
		ArrayUtils.copyByteArray(buffer, BufferHead, encryptedDB, BufferHead, dbSize);
		// encrypt dbIndex
		byte[] encryptedIndex;
		short indexSize = encryptFiles(indexData, buffer);
		encryptedIndex = new byte[indexSize];
		ArrayUtils.copyByteArray(buffer, BufferHead, encryptedIndex, BufferHead, indexSize);

		// concatenate updated DB and Response
		int myResponseLength = myResponse.length;
		byte[] newResponse = new byte[myResponseLength+6+dbSize+indexSize];

		// copy sizes (DB and Index)
		ArrayUtils.copyByteArray(shortToByteArray((short) indexSize),BufferHead,newResponse,BufferHead,HeaderSize);
		ArrayUtils.copyByteArray(shortToByteArray((short) dbSize),BufferHead,newResponse,DataSizeHeader,HeaderSize);

		// copy dbCount
		ArrayUtils.copyByteArray(shortToByteArray(dbCount), BufferHead, newResponse, DataCountHeader, HeaderSize);

		// copy DB index
		ArrayUtils.copyByteArray(encryptedIndex, BufferHead, newResponse, IndexOffset, indexSize);

		// copy DB Data
		ArrayUtils.copyByteArray(encryptedDB, BufferHead, newResponse, IndexOffset+indexSize, dbSize);

		// copy original Response Data
		ArrayUtils.copyByteArray(myResponse, BufferHead, newResponse, IndexOffset+indexSize+dbSize, myResponseLength);

		// set myResponse to the new Response content
		myResponse = new byte[newResponse.length+1];
		// put dirty flag inside the response
		myResponse[0]=DBDirtyFlag;
		ArrayUtils.copyByteArray(newResponse, BufferHead, myResponse, 1, newResponse.length);
		return myResponse;
	}
}
