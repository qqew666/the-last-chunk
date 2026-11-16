package dev.mirnaubyl;
public final class RoundClockTest {
 public static void main(String[] args) {
  RoundClock c = new RoundClock(0);
  for(int i=0;i<5999;i++) if(c.tick()) throw new AssertionError("Early round");
  if(!c.tick()) throw new AssertionError("Missing 5 minute round");
  for(int i=0;i<100;i++) c.tick();
  if(c.elapsed()!=6000) throw new AssertionError("Overflow while choosing");
  if(!new RoundClock(c.elapsed()).ready()) throw new AssertionError("Save/restore");
  c.reset(); if(c.ready() || c.elapsed()!=0) throw new AssertionError("Reset");
  if(new RoundClock(-1).elapsed()!=0) throw new AssertionError("Invalid save");
  System.out.println("Round clock: all checks passed");
 }
}
