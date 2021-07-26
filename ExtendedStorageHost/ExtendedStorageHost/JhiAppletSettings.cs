namespace ExtendedStorageHost
{
    public class JhiAppletSettings
    {
        public JhiAppletSettings(string id, string path)
        {
            Path = path;
            Id = id;
        }

        public string  Path { get; private set; }
        public string Id { get; private set; }
    }
}
