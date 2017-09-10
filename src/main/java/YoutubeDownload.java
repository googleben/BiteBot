//import com.github.axet.vget.VGet;

import com.github.axet.vget.VGet;
import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import net.bramp.ffmpeg.job.FFmpegJob;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class YoutubeDownload {
    
    private static final ExecutorService pool = Executors.newFixedThreadPool(10);
    
    public static Future<File> download(String url, String name, int start, int end) {
        return pool.submit(() -> {
            File f = downloadH(url, name);
            trim(f.getAbsolutePath(), start, end);
            return f;
        });
    }
    
    private static Future<File> download(String url, String name) {
        return pool.submit(() -> downloadH(url, name));
    }
    
    private static File downloadH(String url, String name) {
        File result = Paths.get("./downloads").toAbsolutePath().normalize().toFile();
        System.out.println(result);
        try {
            VGet v = new VGet(new URL(url), result);
            
            v.download();
            return v.getTarget();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    private static FFmpeg ffmpeg;
    private static FFprobe ffprobe;
    private static FFmpegExecutor ffExecutor;
    
    static {
        try {
            ffmpeg = new FFmpeg("ffmpeg.exe");
            ffprobe = new FFprobe("ffprobe.exe");
            ffExecutor = new FFmpegExecutor(ffmpeg, ffprobe);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public static void trim(String file, int start, int end) {
        FFmpegBuilder f = new FFmpegBuilder();
        f.setInput(file)
                .overrideOutputFiles(true)
                .addOutput(file.replace(".webm", ".wav"))
                .setFormat("wav")
                .setAudioChannels(2)
                .setStartOffset(start, TimeUnit.MILLISECONDS)
                .setDuration(end-start, TimeUnit.MILLISECONDS)
                .done();
        FFmpegJob j = ffExecutor.createJob(f);
        j.run();
    }
    
}
