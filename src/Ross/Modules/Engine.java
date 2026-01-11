package Ross.Modules;

import org.lwjgl.glfw.GLFW;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Engine {

    public boolean run = false;
    public ExecutorService pool = null;
    public Engine (JobModule jobModule) {
        jobModule.setEngine(this);
    }

    protected Runnable updateLoop(JobModule jobModule) {
        return () -> {

            final long TIME_PER_TICK = (long) (1e9 / Settings.maxTPS);
            long lastTime, thisTime, elapsedTime, sleepTime = 0;
            double deltaTime;
            lastTime = System.nanoTime();



            while(run) {
                thisTime = System.nanoTime();
                elapsedTime = thisTime-lastTime;
                deltaTime = elapsedTime / 1e9;
                sleepTime += TIME_PER_TICK - elapsedTime;
                lastTime = thisTime;
//
//                float fps = (float) (1/deltaTime);
//                if(fps > 0){
//                    System.out.format("%.2f", fps);
//                    System.out.println("  and  " +  deltaTime*1e3 + " ms");
//
//                }
                ///////////////

                //from futures get function, maybe handle differently in the future.
                try {
                    jobModule.updateJobs(deltaTime);
                } catch (ExecutionException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }


                sleep( sleepTime);




            }
            pool.shutdown();

        };
    }
    protected void renderLoop(JobModule jobModule) {

        final long TIME_PER_TICK = (long) (1e9 / Settings.maxFPS);
        long lastTime, thisTime, elapsedTime, sleepTime = 0;
        double deltaTime;
        lastTime = System.nanoTime();


        while(run) {
            thisTime = System.nanoTime();
            elapsedTime = thisTime-lastTime;
            deltaTime = elapsedTime / 1e9;
            sleepTime += TIME_PER_TICK - elapsedTime;
            lastTime = thisTime;


            jobModule.renderJobs(deltaTime);

            jobModule.getWindow().update();



            run = !jobModule.getWindow().isClosed();


            sleep( sleepTime);
        }
    }

    protected ExecutorService getPool(int N_THREADS) {
        ExecutorService pool = Executors.newWorkStealingPool(N_THREADS);
        this.pool = pool;
        return pool;
    }
    private void sleep(long time) {
        if(time<=0) {
            return;
        }
        long millis = (long) (time/1e6);
        int nanos = (int) (time-millis*1e6);
        try {
            Thread.sleep(millis, nanos);
        } catch( InterruptedException e){
            System.err.println("Error in game loop . . .");
            e.printStackTrace();
        }
    }
}
