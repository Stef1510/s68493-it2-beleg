import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FECpacket
{
  int FEC_group; // Anzahl an Medienpaketen für eine Gruppe
  byte[] buffer;
  
  List<Integer> seqnrlist;  //Liste mit den Sequenznummern des FEC Paketes
  List<RTPpacket> packetslist = new ArrayList<>();  //Liste mit den vorhanden Paketen
  
  int buffersize, packets, seqnb, lastseqnb, repaired;
  
  final static int FEC_TYPE = 127;
  
  public FECpacket(){
    this(0);
  }
  
  public FECpacket( int k) {
    initFec();
    FEC_group = k;
  }
  
  // speichert UDP-Payload, Nr. des Bildes
  //wird von Client aufgerufen, wenn er ein Packet mit dem Payloadtype 26 erhält
  public void rcvdata(RTPpacket rtppacket){
    if (lastseqnb < rtppacket.getsequencenumber()) {
      //hinzufügen des RTPPaketes in die packetslist
      packetslist.add(rtppacket);
      packets++;
      lastseqnb = rtppacket.getsequencenumber();
    }
  }
  
  // speichert FEC-Daten, Nr. eines Bildes der Gruppe
  //wird von Client aufgerufen, wenn er ein Packet mit dem Payloadtype 127 erhält
  public void rcvfec(RTPpacket rtp){
    //fec-gruppengröße ist das erste byte des arrays
    FEC_group = rtp.payload[0];
    
    //-1 weil das byte der fecgruppe weggerechnet wird
    buffersize = rtp.getpayload_length() - 1;
    
    // ab 1, da copy ohne FEC-Group erfolgen soll
    byte[] newdata = Arrays.copyOfRange(rtp.payload, 1, rtp.getpayload_length());
    addToFecBuffer(newdata, buffersize);
    
    seqnb = rtp.getsequencenumber();
    
    //prüft ob Paket repariert wurde
    if(checkrepair()){
      repaired ++;
    }
    
    initFec();
  }
  
  //XOR-verknüpfen neuer Daten mit dem bereits bekannten Daten
  void addToFecBuffer(byte[] bytes, int bytes_length) { // nimmt Nutzerdaten entgegen
    if (bytes_length > buffer.length) {
      
      // temporäres hilfsarray anlegen
      byte[] temp = new byte[bytes_length];
      
      // befülle das temp array mit den bereits im buffer vorhandenen Paketen
      System.arraycopy(buffer, 0, temp, 0, buffer.length);
      
      buffer = temp;
      buffersize = bytes_length;
    }
    
    // verknüpfe bekannte Daten mit neuen Daten des nächsten Frames
    for (int i = 0; i < bytes_length; i++) {
      buffer[i] = (byte) (buffer[i] ^ bytes[i]);
    }
    
    packets++;
  }
  
  //prüft ob mehr als ein Paket verloren gegangen ist und
  //wiederhergestellt werden konnte
  private boolean checkrepair() {
    for (int i = 0; i < packetslist.size(); i++) {
      if (packetslist.get(i).getsequencenumber() > seqnb - FEC_group) {
        //hinzufügen der Sequenznummer in die Sequenznummernliste
        seqnrlist.add(packetslist.get(i).getsequencenumber());
      }
    }
    
    if (seqnrlist.size() == FEC_group) {  // Alle Pakete vorhanden :-)
      return false;
    } else if (seqnrlist.size() < FEC_group - 1) { // Mehr als ein Paket verloren, nicht rekonstruierbar :-(
      return false;
    } else { // genau ein Paket verloren, rekonstruierbar :-)
      
      //verknüpft die vorhandenen Pakete des FEC-Paket, aus dem byte[] lässt sich das fehlende Bild rekonstruieren
      for (int i = 0; i < packetslist.size(); i++) {
        if (packetslist.get(i).getsequencenumber() > seqnb - FEC_group ) {
          addToFecBuffer(packetslist.get(i).payload, packetslist.get(i).payload_size);
        }
      }
      
      // erhalte die die Nummer des fehlenden Paketes
      int missingnr = getMissedNr();
      
      //rekonstruiere fehlendes Paket aus den im FECPaket befindlichen Daten
      RTPpacket missingpacket = new RTPpacket(26, missingnr, 0, buffer, buffer.length);
      
      // temporäre Liste um das Paket an die richtige Stelle einzuordnen
      List<RTPpacket> temp = new ArrayList<>();
      
      // speichere alle Pakete die nach dem fehlenden Paket liegen in eine temporäre Liste
      // und lösche die Pakete aus der packetslist
      while ((packetslist.size() > 0)
              && (packetslist.get(packetslist.size() - 1).getsequencenumber() > missingnr)) {
        temp.add(0, packetslist.get(packetslist.size() - 1));
        packetslist.remove(packetslist.size() - 1);
      }
      
      //Paket an der korrekten Stelle hinzufügen
      packetslist.add(missingpacket);
      
      // Pakete aus tmp Liste anhängen und tmp liste leeren
      while (temp.size() > 0) {
        packetslist.add(temp.get(0));
        temp.remove(0);
      }
      
      return true;
    }
  }
  
  //gibt aus welche Sequenznummer fehlt
  int getMissedNr() {
    int miss = seqnb - FEC_group;
    for (int i = 0; i < seqnrlist.size(); i++) {
      if (seqnrlist.get(i) == miss + 1) {
        miss = seqnrlist.get(i);
      } else {
        return miss + 1;
      }
    }
    return seqnb;
  }
  
  //gibt erstes Paket der Paketliste zurück und löscht anschließend dieses aus der Paketliste
  public byte[] getjpeg( int nr){
    if (packetslist.size() > 0) {
      RTPpacket rtp_packet = packetslist.get(0);
      int payload_length = rtp_packet.getpayload_length();
      byte[] payload = new byte[payload_length];
      rtp_packet.getpayload(payload);
      
      packetslist.remove(0);
      
      return payload;
    } else {
      return null;
    }
  }
  
  //erstellt FEC-Paket aus den im Buffer vorhandenen Daten
  RTPpacket createRtpPacket(int imagenb) {
    byte[] fec = new byte[buffersize + 1];
    fec[0] = (byte) packets;
    //kopiert daten aus buffer ab Stelle 0 in fec ab stelle 1
    System.arraycopy(buffer, 0, fec, 1, buffersize);
    
    return new RTPpacket(FEC_TYPE, imagenb, imagenb * 40, fec, buffersize + 1);
  }
  
  final void initFec() {
    buffer = new byte[0];
    buffersize = 0;
    seqnrlist = new ArrayList<>();
    seqnb = 0;
    packets = 0;
    FEC_group = 0;
  }
  
  //Gibt die Anzahl der reparierten Pakete zurück
  public int getrepaired(){
    return repaired;
  }
}

