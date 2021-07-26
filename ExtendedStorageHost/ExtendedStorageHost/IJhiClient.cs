namespace ExtendedStorageHost
{
    public interface IJhiClient
    {
        void Install();
        void CreateSession(bool shared, byte[] initBuffer);
        void SendAndRecv(int cmdId, byte[] sendBuff, ref byte[] recvBuff, out int responseCode);
        void CloseSession();
        void Uninstall();
    }
}
