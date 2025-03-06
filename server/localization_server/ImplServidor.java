package server.localization_server;

import java.io.IOException;
import java.net.Socket;
import java.util.Scanner;

public class ImplServidor implements Runnable {
    public Socket socketCliente;
    public static int cont = 0;
    private boolean conexao = true;
    private Scanner s = null;

    public ImplServidor(Socket cliente) {
        socketCliente = cliente;
    }

    public void run() {
        String mensagemRecebida;
        System.out.println("Conexão " + 
        ImplServidor.cont +
                " com o cliente " +
                socketCliente.getInetAddress().getHostAddress() +
                "/" +
                socketCliente.getInetAddress().getHostName());
        try {
            s = new Scanner(socketCliente.getInputStream());
            // Exibe mensagem no console
            while (conexao) {
                mensagemRecebida = s.nextLine();
                if (mensagemRecebida.equalsIgnoreCase("fim"))
                    conexao = false;
                else
                    System.out.println(mensagemRecebida);
            }
            // Finaliza scanner e socket
            s.close();
            System.out.println("Fim do cliente " +
                    socketCliente.getInetAddress().getHostAddress());
            socketCliente.close();
        } catch (IOException e) {
            e.getMessage();
        }
    }
}