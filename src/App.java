import client.Client;
import client.gui.MainMenu;
import server.location_server.ServerLocalization;
import server.proxy_server.ServerProxy;

public class App {
    public static void main(String[] args) throws Exception {
        // new ServerApplication();
        new ServerLocalization();
        new ServerProxy();
        MainMenu mainMenu = new MainMenu();
        new Client(mainMenu);

    }
}
