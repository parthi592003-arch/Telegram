package org.webrtc.voiceengine;

import java.nio.ByteBuffer;

/**
 * Loud "metal bass" voice effect for 16-bit mono PCM.
 *
 * Chain: high-pass -> bass boost -> huge gain with tanh clipping (distortion)
 *        -> extra low end after the distortion -> high-frequency fizz cut -> final limiter.
 *
 * Create ONE instance per recording (it keeps filter state between buffers).
 * Tune the constants below to taste.
 */
public final class LoudProcessor {

  // Pre-distortion gain. Above ~30x speech is already clipped to full scale,
  // so higher values mostly make background noise louder, not your voice.
  private static final float GAIN = 100f;

  // Low-frequency boost mixed in BEFORE the distortion (thickens the voice).
  private static final float BASS = 0.8f;

  // Low-frequency boost mixed in AFTER the distortion (the "metal bass" thump).
  private static final float POST_BASS = 0.6f;

  // Cut-off of the filter that tames harsh distortion fizz (Hz). Lower = darker.
  private static final float FIZZ_CUT_HZ = 5000f;

  // Final output level before the limiter.
  private static final float OUT_LEVEL = 1.2f;

  private final float hpA;
  private final float lpA;
  private final float postLpA;
  private final float fizzA;

  private float hpX = 0f, hpY = 0f;
  private float lp = 0f;
  private float postLp = 0f;
  private float fizz = 0f;

  public LoudProcessor(int sampleRate) {
    if (sampleRate <= 0) {
      sampleRate = 48000;
    }
    hpA = (float) Math.exp(-2.0 * Math.PI * 80.0 / sampleRate);
    lpA = (float) (1.0 - Math.exp(-2.0 * Math.PI * 200.0 / sampleRate));
    postLpA = (float) (1.0 - Math.exp(-2.0 * Math.PI * 120.0 / sampleRate));
    fizzA = (float) (1.0 - Math.exp(-2.0 * Math.PI * FIZZ_CUT_HZ / sampleRate));
  }

  /** Processes bytesRead bytes of 16-bit PCM in place (absolute indexing, buffer position unchanged). */
  public void process(ByteBuffer buf, int bytesRead) {
    int n = bytesRead / 2;
    for (int i = 0; i < n; i++) {
      float x = buf.getShort(i * 2) / 32768f;

      // remove rumble / DC
      float y = hpA * (hpY + x - hpX);
      hpX = x;
      hpY = y;

      // bass boost before distortion
      lp += lpA * (y - lp);
      float v = y + BASS * lp;

      // distortion
      float d = (float) Math.tanh(v * GAIN);

      // extra low end after distortion
      postLp += postLpA * (d - postLp);
      d = d + POST_BASS * postLp;

      // tame the harsh highs
      fizz += fizzA * (d - fizz);

      // final limiter
      float o = (float) Math.tanh(fizz * OUT_LEVEL);
      buf.putShort(i * 2, (short) (o * 32767f));
    }
  }
}
