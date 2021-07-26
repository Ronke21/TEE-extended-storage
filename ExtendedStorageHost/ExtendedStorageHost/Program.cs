using System;
using System.Text;
using Intel.Dal;

namespace ExtendedStorageHost
{
    class Program
    {
        static void Main(string[] args)
        {
#if AMULET
            Jhi.DisableDllValidation = true;
#endif
            string appletID = "bd0f8e87-c86d-46ec-a389-cce3f7ea07ac";
            string appletPath = @"C:\Users\shai\workspace\ExtendedStorage\bin\ExtendedStorage.dalp";

            JhiAppletSettings settings = new JhiAppletSettings(appletID, appletPath);

            string filePath = @"C:\Users\shai\My Documents\Visual Studio 2015\Projects\ExtendedStorageHost\ExtendedStorageHost\bin\Amulet\db.bin";
            JhiClientWithExtendedStorage _client = new JhiClientWithExtendedStorage(settings, filePath);

            // Install the Trusted Application
            Console.WriteLine("Installing the applet.");
            _client.Install();

            // Open Session
            byte[] initBuffer = new byte[] { };
            Console.WriteLine("Opening a session.");
            _client.CreateSession(false, initBuffer);
            byte[] recvBuff;
            int responseCode, cmdId;

            WriteAndRead(_client, out recvBuff, out responseCode, out cmdId);

            //Read(_client, out recvBuff, out responseCode, out cmdId);

            // Close the session
            Console.WriteLine("Closing the session.");
            _client.CloseSession();

            //Uninstall the Trusted Application
            Console.WriteLine("Uninstalling the applet.");
            _client.Uninstall();

            Console.WriteLine("Press Enter to finish.");
            Console.Read();
        }

        private static void WriteAndRead(JhiClientWithExtendedStorage _client, out byte[] recvBuff, out int responseCode, out int cmdId)
        {
            Write(_client, out recvBuff, out responseCode, out cmdId);

            Read(_client, out recvBuff, out responseCode, out cmdId);
        }

        private static void Write(JhiClientWithExtendedStorage _client, out byte[] recvBuff, out int responseCode, out int cmdId)
        {
            // Send and Receive data to/from the Trusted Application
            byte[] sendBuff = UTF32Encoding.UTF8.GetBytes("Hello"); // A message to send to the TA
            recvBuff = new byte[2000];
            cmdId = 0;
            Console.WriteLine("Performing send and receive operation.");
            _client.SendAndRecv(cmdId, sendBuff, ref recvBuff, out responseCode);
            Console.Out.WriteLine("Response buffer is " + UTF32Encoding.UTF8.GetString(recvBuff));

            cmdId = 1;
            sendBuff = UTF32Encoding.UTF8.GetBytes("World");
            recvBuff = new byte[2000]; // A buffer to hold the output data from the TA
            Console.WriteLine("Performing send and receive operation.");
            _client.SendAndRecv(cmdId, sendBuff, ref recvBuff, out responseCode);
            Console.Out.WriteLine("Response buffer is " + UTF32Encoding.UTF8.GetString(recvBuff));
        }

        private static void Read(JhiClientWithExtendedStorage _client, out byte[] recvBuff, out int responseCode, out int cmdId)
        {
            cmdId = 0;
            recvBuff = new byte[2000]; // A buffer to hold the output data from the TA
            Console.WriteLine("Performing send and receive operation.");
            _client.SendAndRecv(cmdId, null, ref recvBuff, out responseCode);
            Console.Out.WriteLine("Response buffer is " + UTF32Encoding.UTF8.GetString(recvBuff));

            cmdId = 1;
            recvBuff = new byte[2000]; // A buffer to hold the output data from the TA
            Console.WriteLine("Performing send and receive operation.");
            _client.SendAndRecv(cmdId, null, ref recvBuff, out responseCode);
            Console.Out.WriteLine("Response buffer is " + UTF32Encoding.UTF8.GetString(recvBuff));
        }
    }
}