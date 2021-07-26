using Intel.Dal;
using System;
using System.IO;
using System.Linq;

namespace ExtendedStorageHost
{
    /// <summary>
    /// A simple class that implement most basic functionality of Jhi Host Application.
    /// currently, support single Session connection and without shared flag consideration(might not work with this class).
    /// Usage:
    /// This class is unopinionated implementation.
    /// So you need to call Install and Uninstall in your code when it's appropiate.
    /// Then, between the Install and Uninstall calls you can create new session and work with it.
    /// when you finished with the session you can close it for the time being the DB will still work.
    /// </summary>
    public class JhiClientWithExtendedStorage : IJhiClient
    {
        public const byte DBExistsOrUpdatedFlag = 1;
        public const byte DBNotExistsFlag = 0;

        public const short HeaderSize = 2;
        public const short BufferHead = 0;
        public const short DataSizeHeader = 2;
        public const short DataCountHeader = 4;
        public const short IndexOffset = 6;

        private Jhi _jhi = Jhi.Instance;
        private JhiSession _session;
        private JhiAppletSettings _settings;

        private EncryptedDB _db;
        private string _filePath;

        /// <summary>
        /// A Constructor that gets Applets settings and a file path to save DB in it.
        /// </summary>
        /// <param name="settings">Applet ID and Path for connecting to the applet</param>
        /// <param name="filePath">A location for to save the DB encrypted data</param>
        public JhiClientWithExtendedStorage(JhiAppletSettings settings, string filePath)
        {
            _settings = settings;
            _filePath = filePath;
            _db = new EncryptedDB();
            if (File.Exists(_filePath))
            {
                var fileData = File.ReadAllBytes(_filePath);

                ReadDBfromBuffer(fileData);
            }
            else
            {
                _db.Index = new byte[] { };
                _db.Data = new byte[] { };
            }
        }
        /// <summary>
        /// A private helper function that read DB data from buffer
        /// </summary>
        /// <param name="buffer">the byte array buffer taht hold that DB data</param>
        private void ReadDBfromBuffer(byte[] buffer)
        {
            short indexLen = BitConverter.ToInt16(buffer, BufferHead);
            short dataLen = BitConverter.ToInt16(buffer, DataSizeHeader);
            _db.Index = new byte[indexLen];
            _db.Data = new byte[dataLen];
            _db.Count = BitConverter.ToInt16(buffer, DataCountHeader);
            Array.ConstrainedCopy(buffer, IndexOffset, _db.Index, BufferHead, indexLen);
            Array.ConstrainedCopy(buffer, IndexOffset + indexLen, _db.Data, BufferHead, dataLen);
        }
        /// <summary>
        /// A private helper function that extract and override response data with the original response data the applet sent.
        /// </summary>
        /// <param name="recvBuff">The buffer we recieved from Applet</param>
        /// <returns>The original response</returns>
        private byte[] extractResponse(byte[] recvBuff)
        {
            short indexLen = BitConverter.ToInt16(recvBuff, BufferHead);
            short dataLen = BitConverter.ToInt16(recvBuff, DataSizeHeader);
            int responseOffset = IndexOffset + indexLen + dataLen;
            int responseLen = recvBuff.Count() - responseOffset;
            byte[] tempBuffer = new byte[responseLen];
            Array.ConstrainedCopy(recvBuff, responseOffset, tempBuffer, BufferHead, responseLen);
            recvBuff = new byte[responseLen];
            Array.ConstrainedCopy(tempBuffer, BufferHead, recvBuff, BufferHead, responseLen);
            return recvBuff;
        }
        /// <summary>
        /// A private helper function that save encrypted DB from buffer in disk.
        /// (the function only takes the DB and ignore the rest)
        /// </summary>
        private void saveDBToFile()
        {
            short indexLen = (short)_db.Index.Count();
            short dataLen = (short)_db.Data.Count();

            byte[] fileData = new byte[IndexOffset + indexLen + dataLen];
            Array.ConstrainedCopy(BitConverter.GetBytes(indexLen), BufferHead, fileData, BufferHead, HeaderSize);
            Array.ConstrainedCopy(BitConverter.GetBytes(indexLen), BufferHead, fileData, DataSizeHeader, HeaderSize);
            Array.ConstrainedCopy(BitConverter.GetBytes(_db.Count), BufferHead, fileData, DataCountHeader, HeaderSize);

            Array.ConstrainedCopy(_db.Index, BufferHead, fileData, IndexOffset, indexLen);
            Array.ConstrainedCopy(_db.Data, BufferHead, fileData, IndexOffset + indexLen, dataLen);

            File.WriteAllBytes(_filePath, fileData);
        }
        /// <summary>
        /// Close current session with the Applet.
        /// </summary>
        public void CloseSession()
        {
            _jhi.CloseSession(_session);
        }
        /// <summary>
        /// Create a new Session with the applet.
        /// </summary>
        /// <param name="shared">a flag that indicate if we want normal or shared session</param>
        /// <param name="initBuffer">a buffer with data we want to send to the applet onInit function</param>
        public void CreateSession(bool shared, byte[] initBuffer)
        {
            short indexSize = (short)_db.Index.Count();
            short dataSize = (short)_db.Data.Count();
            short initSize = (short)initBuffer.Count();

            byte[] payload;
            if (dataSize > 0 && indexSize > 0)
            {
                payload = new byte[1 + IndexOffset + indexSize + dataSize + initSize];
                payload[0] = DBExistsOrUpdatedFlag;
                int offset = 1;
                Array.ConstrainedCopy(BitConverter.GetBytes(indexSize), BufferHead, payload, offset, HeaderSize);
                offset += HeaderSize;

                Array.ConstrainedCopy(BitConverter.GetBytes(dataSize), BufferHead, payload, offset, HeaderSize);
                offset += HeaderSize;

                Array.ConstrainedCopy(BitConverter.GetBytes(_db.Count), BufferHead, payload, offset, HeaderSize);
                offset += HeaderSize;

                Array.ConstrainedCopy(_db.Index, BufferHead, payload, offset, indexSize);
                offset += indexSize;

                Array.ConstrainedCopy(_db.Data, BufferHead, payload, offset, dataSize);
                offset += dataSize;

                Array.ConstrainedCopy(initBuffer, BufferHead, payload, offset, initSize);
            }
            else
            {
                payload = new byte[1 + initSize];
                payload[0] = DBNotExistsFlag;
                Array.ConstrainedCopy(initBuffer, BufferHead, payload, 1, initSize);
            }

            _jhi.CreateSession(_settings.Id,
                shared ? JHI_SESSION_FLAGS.SharedSession : JHI_SESSION_FLAGS.None
                , payload, out _session);
        }
        /// <summary>
        /// Install the Applet in the TEE.
        /// </summary>
        public void Install()
        {
            _jhi.Install(_settings.Id, _settings.Path);
        }
        /// <summary>
        /// A function that send data to Applet
        /// </summary>
        /// <param name="cmdId">The command Id</param>
        /// <param name="sendBuff">The Command Data</param>
        /// <param name="recvBuff">The response data from the Applet</param>
        /// <param name="responseCode">The response code from the applet</param>
        public void SendAndRecv(int cmdId, byte[] sendBuff, ref byte[] recvBuff, out int responseCode)
        {   
            _jhi.SendAndRecv2(_session, cmdId, sendBuff, ref recvBuff, out responseCode);
            if (recvBuff[0] == DBExistsOrUpdatedFlag) // DB was Updated.
            {
                // update DB
                var responseDataLength = recvBuff.Count() - 1;
                byte[] responseData = new byte[responseDataLength];
                Array.ConstrainedCopy(recvBuff, 1, responseData, BufferHead, responseDataLength);
                ReadDBfromBuffer(responseData);

                // save DB to File
                saveDBToFile();

                // return only the original Response data
                recvBuff = extractResponse(responseData);
            }
        }
        /// <summary>
        /// Uninstall the Applet from TEE.
        /// </summary>
        public void Uninstall()
        {
            _jhi.Uninstall(_settings.Id);
        }
    }
}
