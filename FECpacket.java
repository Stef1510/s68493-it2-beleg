import java.util.Arrays;

public class FECpacket
{
    int FEC_group;       // FEC-Gruppengröße
     
    byte[][] mediastack; // Puffer für Medienpakete
    byte[][] fecstack;   // Puffer für FEC-Pakete 
    
    // SENDER --------------------------------------
    public FECpacket()

    // RECEIVER ------------------------------------
    public FECpacket( int FEC_group)
        
    
    // ----------------------------------------------
    // *** SENDER *** 
    // ----------------------------------------------
    
    // speichert Nutzdaten zur FEC-Berechnung
    public void setdata( byte[] data, int data_length) 
    
    // holt fertiges FEC-Paket, Rückgabe: Paketlänge 
    public int getdata( byte[] data)
    


    // ------------------------------------------------
    // *** RECEIVER *** 
    // ------------------------------------------------
    // speichert UDP-Payload, Nr. des Bildes
    public void rcvdata( int nr, byte[] data)

    // speichert FEC-Daten, Nr. eines Bildes der Gruppe    
    public void rcvfec( int nr, byte[] data)
    
    // übergibt vorhandenes/korrigiertes Paket oder Fehler (null)    
    public byte[] getjpeg( int nr)
    
    // für Statistik, Anzahl der korrigierten Pakete
    public int getNrCorrected()
}
