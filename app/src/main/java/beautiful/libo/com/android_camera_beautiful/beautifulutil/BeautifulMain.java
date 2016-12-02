package beautiful.libo.com.android_camera_beautiful.beautifulutil;

import android.graphics.Bitmap;

import java.util.concurrent.CyclicBarrier;

/**
 * Created by ALiSir on 2016/12/2.
 */

public class BeautifulMain {

    /**
     * 实现步骤
     1.用具有保边效果的滤波算法对图像进行模糊处理
     2.用肤色检测算法保护非皮肤区域
     3.将模糊后的图像和原图进行图像融合
     4.对融合后的图像进行锐化处理

     对于步骤一，该滤波算法可以选择双边滤波，导向滤波，表面模糊等，只要能保边缘就行，高斯模糊是不行的，色斑逗逗就是在这一步磨掉的哈哈，这一步运算速度将直接影响到最后美颜的效率，这也是可以各显神通的地方。

     对于步骤二，第一次听说肤色检测好像很高大上，但是它的算法非常简单，就是根据像素的rgb值去判断而已

     对于步骤三，可以采用基于alpha通道的图像融合，这一步的作用是为了增加皮肤的质感，因为第一步一般都把皮肤磨得跟娃娃一样了感觉很假。

     对于步骤四，在步骤三处理后，会发现图像还是有点朦胧感，还是第一步的副作用，锐化可以强化边缘，让图像看起来更清晰，关于锐化的算法网上有不同的实现算法
     */

    /**
     * 核心函数，请不要在主线程调用
     * params：bit原图，sigma美颜程度建议范围（1-20）
     * return 美颜后的图片
     */
    public Bitmap beautifyImg(Bitmap bit, int sigma) {
        final int width = bit.getWidth();
        final int height = bit.getHeight();
        //原图
        int[] src_pixels = new int[width * height];

        //结果图
        int[] res_pixels = new int[width * height];

        //将src_pixels中的每个数赋值
        bit.getPixels(src_pixels, 0, width, 0, 0, width, height);

        //将图片分成5块进行处理，每块启动一个线程处理图片
        int div = height / 5;

        //范围
        int radius = (int) (Math.max(width, height) * 0.02);
        CyclicBarrier barrier = new CyclicBarrier(5);
        Thread t1 = new Thread(new FilterTask(barrier, src_pixels, res_pixels, width, height, radius, sigma, 50, 0, div));
        Thread t2 = new Thread(new FilterTask(barrier, src_pixels, res_pixels, width, height, radius, sigma, 50, div + 1, 2 * div));
        Thread t3 = new Thread(new FilterTask(barrier, src_pixels, res_pixels, width, height, radius, sigma, 50, 2 * div + 1, 3 * div));
        Thread t4 = new Thread(new FilterTask(barrier, src_pixels, res_pixels, width, height, radius, sigma, 50, 3 * div + 1, 4 * div));
        Thread t5 = new Thread(new FilterTask(barrier, src_pixels, res_pixels, width, height, radius, sigma, 50, 4 * div + 1, height - 1));

        t1.start();
        t2.start();
        t3.start();
        t4.start();
        t5.start();
        try {
            //join()主线程等待子线程完成后，再结束
            t1.join();
            t2.join();
            t3.join();
            t4.join();
            t5.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Bitmap resImg = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        resImg.setPixels(src_pixels, 0, width, 0, 0, width, height);
        return resImg;
    }

    public boolean isSkin(int r, int g, int b) {
        if (r > 95 && g > 40 && b > 20 && r > g && r > b && (max(r, g, b) - min(r, g, b) > 15) && Math.abs(r - g) > 15) {
            return true;
        } else {
            return false;
        }
    }

    public int min(int a, int b, int c) {
        if (a > b)
            a = b;
        if (a > c)
            a = c;
        return a;
    }

    public int max(int a, int b, int c) {
        if (a < b)
            a = b;
        if (a < c)
            a = c;
        return a;
    }

    public class FilterTask implements Runnable {
        int[] src, res;
        int width, height, radius, sigma, startRaw, endRaw, alpha;
        CyclicBarrier barrier;

        public FilterTask(CyclicBarrier barriers, int[] src, int[] res, int width, int height, int radius, int sigma, int alpha, int startRaw, int endRaw) {
            this.barrier = barriers;
            this.src = src;
            this.res = res;
            this.width = width;
            this.height = height;
            this.radius = radius;
            this.sigma = sigma;
            this.startRaw = startRaw;
            this.endRaw = endRaw;
            this.alpha = alpha;

        }

        @Override
        public void run() {
            //滤波+图层混合+肤色识别
            varMeanFilter(src, res, width, height, radius, sigma, startRaw, endRaw, alpha);
            try {
                barrier.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
            //均值滤波的锐化算法
            sharpen(res, src, width, height, 10, 2, startRaw, endRaw);
        }
    }

    //滤波+图层混合+肤色识别
    public int[] varMeanFilter(int[] array, int[] res, int width, int height, int radius, int sigma, int startRaw, int endRaw, int alpha) {
        sigma = 10 + sigma * sigma * 5;
        //和数组
        int[] rwindow = new int[width];
        int[] gwindow = new int[width];
        int[] bwindow = new int[width];
        //平方和数组
        int[] r_squ_window = new int[width];
        int[] g_squ_window = new int[width];
        int[] b_squ_window = new int[width];
        //窗口面积
        int filter_win = (radius * 2 + 1);
        filter_win = filter_win * filter_win;
        //窗口内的rgb值得和
        int rsum = 0, bsum = 0, gsum = 0;
        //窗口内的rgb值得平方的和
        int r_squ_sum = 0, b_squ_sum = 0, g_squ_sum = 0;
        //新的rgb值
        int new_r = 0, new_g = 0, new_b = 0;
        //旧的rgb值
        int old_r = 0, old_g = 0, old_b = 0, oldP = 0;
        //窗口平均值
        int mean_r = 0, mean_g = 0, mean_b = 0;
        //窗口方差
        float var_r = 0, var_g = 0, var_b = 0, tmp = 0;
        //窗口增加值，和删除值
        int addp = 0, subp = 0;

        //初始化window数组
        for (int y = 0; y < width; y++) {
            int r_col_sum = 0;
            int g_col_sum = 0;
            int b_col_sum = 0;
            int r_squcol_sum = 0;
            int g_squcol_sum = 0;
            int b_squcol_sum = 0;
            for (int x = -radius; x <= radius; x++) {
                int inky = edgeHandle(x + startRaw, height);
                int tmpPixels = array[inky * width + y];
                int b = tmpPixels & 0xff;
                int g = (tmpPixels >> 8) & 0xff;
                int r = (tmpPixels >> 16) & 0xff;
                r_col_sum += r;
                g_col_sum += g;
                b_col_sum += b;
                r_squcol_sum += r * r;
                g_squcol_sum += g * g;
                b_squcol_sum += b * b;
            }
            rwindow[y] = r_col_sum;
            gwindow[y] = g_col_sum;
            bwindow[y] = b_col_sum;

            r_squ_window[y] = r_squcol_sum;
            g_squ_window[y] = g_squcol_sum;
            b_squ_window[y] = b_squcol_sum;
        }

        //开始遍历图片
        for (int i = startRaw; i <= endRaw; i++) {
            //计算第一个sum值
            rsum = 0;
            bsum = 0;
            gsum = 0;
            r_squ_sum = 0;
            b_squ_sum = 0;
            g_squ_sum = 0;
            oldP = array[i * width];
            old_b = oldP & 0xff;
            old_g = (oldP >> 8) & 0xff;
            old_r = (oldP >> 16) & 0xff;
            for (int x = -radius; x <= radius; x++) {
                int inkx = edgeHandle(x, width);
                //算出和
                rsum += rwindow[inkx];
                gsum += gwindow[inkx];
                bsum += bwindow[inkx];
                //平方和
                r_squ_sum += r_squ_window[inkx];
                g_squ_sum += g_squ_window[inkx];
                b_squ_sum += b_squ_window[inkx];
            }
            //根据方差和均值算出新像素
            mean_r = rsum / filter_win;
            mean_g = gsum / filter_win;
            mean_b = bsum / filter_win;
            var_r = ((float) r_squ_sum - (float) rsum * rsum / (float) filter_win) / (float) filter_win;
            var_g = ((float) g_squ_sum - (float) gsum * gsum / (float) filter_win) / (float) filter_win;
            var_b = ((float) b_squ_sum - (float) bsum * bsum / (float) filter_win) / (float) filter_win;
            tmp = var_r / (var_r + sigma);
            new_r = (int) ((1 - tmp) * mean_r + tmp * old_r);
            tmp = var_g / (var_g + sigma);
            new_g = (int) ((1 - tmp) * mean_g + tmp * old_g);
            tmp = var_b / (var_b + sigma);
            new_b = (int) ((1 - tmp) * mean_b + tmp * old_b);

            //融合+肤色检测
            if (isSkin(new_r, new_g, new_b)) {
                new_b = (old_b * alpha + new_b * (255 - alpha)) >> 8;
                new_g = (old_g * alpha + new_g * (255 - alpha)) >> 8;
                new_r = (old_r * alpha + new_r * (255 - alpha)) >> 8;
            } else {
                new_b = old_b;
                new_g = old_g;
                new_r = old_r;
            }

            res[i * width] = ((new_r & 0xff) << 16) | ((new_g & 0xff) << 8) | (new_b & 0xff);


            for (int j = 1; j < width; j++) {
                oldP = array[i * width + j];
                old_b = oldP & 0xff;
                old_g = (oldP >> 8) & 0xff;
                old_r = (oldP >> 16) & 0xff;

                int addx = edgeHandle(j + radius, width);
                int subx = edgeHandle(j - radius - 1, width);

                rsum = rsum + rwindow[addx] - rwindow[subx];
                gsum = gsum + gwindow[addx] - gwindow[subx];
                bsum = bsum + bwindow[addx] - bwindow[subx];

                r_squ_sum = r_squ_sum + r_squ_window[addx] - r_squ_window[subx];
                g_squ_sum = g_squ_sum + g_squ_window[addx] - g_squ_window[subx];
                b_squ_sum = b_squ_sum + b_squ_window[addx] - b_squ_window[subx];

                mean_r = rsum / filter_win;
                mean_g = gsum / filter_win;
                mean_b = bsum / filter_win;
                var_r = ((float) r_squ_sum - (float) rsum * rsum / (float) filter_win) / (float) filter_win;
                var_g = ((float) g_squ_sum - (float) gsum * gsum / (float) filter_win) / (float) filter_win;
                var_b = ((float) b_squ_sum - (float) bsum * bsum / (float) filter_win) / (float) filter_win;

                tmp = var_r / (var_r + sigma);
                new_r = (int) ((1 - tmp) * mean_r + tmp * old_r);
                tmp = var_g / (var_g + sigma);
                new_g = (int) ((1 - tmp) * mean_g + tmp * old_g);
                tmp = var_b / (var_b + sigma);
                new_b = (int) ((1 - tmp) * mean_b + tmp * old_b);

                //融合+肤色检测
                if (isSkin(new_r, new_g, new_b)) {
                    new_b = (old_b * alpha + new_b * (255 - alpha)) >> 8;
                    new_g = (old_g * alpha + new_g * (255 - alpha)) >> 8;
                    new_r = (old_r * alpha + new_r * (255 - alpha)) >> 8;
                } else {
                    new_b = old_b;
                    new_g = old_g;
                    new_r = old_r;
                }

                res[i * width + j] = ((new_r & 0xff) << 16) | ((new_g & 0xff) << 8) | (new_b & 0xff);
            }
            //更新window数组
            for (int y = 0; y < width; y++) {
                addp = edgeHandle(i + radius + 1, height);
                subp = edgeHandle(i - radius, height);
                int tmpPixels = array[subp * width + y];
                old_b = tmpPixels & 0xff;
                old_g = (tmpPixels >> 8) & 0xff;
                old_r = (tmpPixels >> 16) & 0xff;
                tmpPixels = array[addp * width + y];
                new_b = tmpPixels & 0xff;
                new_g = (tmpPixels >> 8) & 0xff;
                new_r = (tmpPixels >> 16) & 0xff;
                rwindow[y] = rwindow[y] + new_r - old_r;
                gwindow[y] = gwindow[y] + new_g - old_g;
                bwindow[y] = bwindow[y] + new_b - old_b;

                r_squ_window[y] = r_squ_window[y] + new_r * new_r - old_r * old_r;
                g_squ_window[y] = g_squ_window[y] + new_g * new_g - old_g * old_g;
                b_squ_window[y] = b_squ_window[y] + new_b * new_b - old_b * old_b;
            }
        }
        return res;
    }

    //边缘处理
    public int edgeHandle(int index, int w) {
        if (index < 0)
            return 0;
        else if (index >= w)
            return w - 1;
        else
            return index;
    }


    //均值滤波的锐化算法
    public int[] sharpen(int[] src, int[] res, int width, int height, int radius, int k, int startRaw, int endRaw) {

        //和数组
        int[] rwindow = new int[width];
        int[] gwindow = new int[width];
        int[] bwindow = new int[width];
        //窗口面积
        int filter_win = (radius * 2 + 1);
        filter_win = filter_win * filter_win;
        //窗口内的rgb值得和
        int rsum = 0, bsum = 0, gsum = 0;
        //新的rgb值
        int new_r = 0, new_g = 0, new_b = 0;
        //旧的rgb值
        int old_r = 0, old_g = 0, old_b = 0, oldP = 0;
        //窗口平均值
        int mean_r = 0, mean_g = 0, mean_b = 0;
        //窗口增加值，和删除值
        int addp = 0, subp = 0;


        //初始化window数组
        for (int y = 0; y < width; y++) {
            int r_col_sum = 0;
            int g_col_sum = 0;
            int b_col_sum = 0;

            for (int x = -radius; x <= radius; x++) {
                int inky = edgeHandle(startRaw + x, height);
                int tmpPixels = src[inky * width + y];
                int b = tmpPixels & 0xff;
                int g = (tmpPixels >> 8) & 0xff;
                int r = (tmpPixels >> 16) & 0xff;
                r_col_sum += r;
                g_col_sum += g;
                b_col_sum += b;

            }
            rwindow[y] = r_col_sum;
            gwindow[y] = g_col_sum;
            bwindow[y] = b_col_sum;
        }

        //开始遍历图片
        for (int i = startRaw; i <= endRaw; i++) {
            //计算第一个sum值
            rsum = 0;
            bsum = 0;
            gsum = 0;

            oldP = src[i * width];
            old_b = oldP & 0xff;
            old_g = (oldP >> 8) & 0xff;
            old_r = (oldP >> 16) & 0xff;
            for (int x = -radius; x <= radius; x++) {
                int inkx = edgeHandle(x, width);
                //算出和
                rsum += rwindow[inkx];
                gsum += gwindow[inkx];
                bsum += bwindow[inkx];

            }
            //根据方差和均值算出新像素
            mean_r = rsum / filter_win;
            mean_g = gsum / filter_win;
            mean_b = bsum / filter_win;


            new_r = range(mean_r + k * (old_r - mean_r));
            new_g = range(mean_g + k * (old_g - mean_g));
            new_b = range(mean_b + k * (old_b - mean_b));

            res[i * width] = ((new_r & 0xff) << 16) | ((new_g & 0xff) << 8) | (new_b & 0xff);


            for (int j = 1; j < width; j++) {
                oldP = src[i * width + j];
                old_b = oldP & 0xff;
                old_g = (oldP >> 8) & 0xff;
                old_r = (oldP >> 16) & 0xff;

                int addx = edgeHandle(j + radius, width);
                int subx = edgeHandle(j - radius - 1, width);

                rsum = rsum + rwindow[addx] - rwindow[subx];
                gsum = gsum + gwindow[addx] - gwindow[subx];
                bsum = bsum + bwindow[addx] - bwindow[subx];


                mean_r = rsum / filter_win;
                mean_g = gsum / filter_win;
                mean_b = bsum / filter_win;

                new_r = range(mean_r + k * (old_r - mean_r));
                new_g = range(mean_g + k * (old_g - mean_g));
                new_b = range(mean_b + k * (old_b - mean_b));
                res[i * width + j] = ((new_r & 0xff) << 16) | ((new_g & 0xff) << 8) | (new_b & 0xff);
            }
            //更新window数组
            for (int y = 0; y < width; y++) {
                addp = edgeHandle(i + radius + 1, height);
                subp = edgeHandle(i - radius, height);
                int tmpPixels = src[subp * width + y];
                old_b = tmpPixels & 0xff;
                old_g = (tmpPixels >> 8) & 0xff;
                old_r = (tmpPixels >> 16) & 0xff;
                tmpPixels = src[addp * width + y];
                new_b = tmpPixels & 0xff;
                new_g = (tmpPixels >> 8) & 0xff;
                new_r = (tmpPixels >> 16) & 0xff;
                rwindow[y] = rwindow[y] + new_r - old_r;
                gwindow[y] = gwindow[y] + new_g - old_g;
                bwindow[y] = bwindow[y] + new_b - old_b;
            }
        }
        return res;
    }

    public int range(int i) {
        if (i < 0)
            return 0;
        else if (i > 255)
            return 255;
        else
            return i;
    }

}