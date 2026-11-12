package com.li.community.common.utils;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.ThreadLocalRandom;
import javax.imageio.ImageIO;

/**
 * 登录/注册滑块验证码图片生成工具 (Java 2D 绘制, 无第三方依赖)
 *
 * <p>每次生成一张程序化绘制的随机渐变纹理背景图与一张拼图块透明图：
 * 拼图块画布高度与背景一致、按真实 y 坐标绘制，前端只需水平移动；
 * 缺口 x 坐标由服务层存入 Redis 用于校验，不随图片下发。</p>
 */
public final class SlideCaptchaUtil {

    /** 背景图尺寸 (前端按自然尺寸 1:1 展示, 保证滑动坐标不缩放) */
    public static final int BG_WIDTH = 310;
    public static final int BG_HEIGHT = 155;

    /** 拼图块画布宽度 (44px 主体 + 凸扣) 与主体边长 */
    public static final int JIGSAW_WIDTH = 54;
    public static final int PIECE_BODY_SIZE = 44;

    /** 缺口坐标随机范围 (避开左右边缘与上下边界) */
    private static final int MIN_TARGET_X = 80;
    private static final int MAX_TARGET_X = BG_WIDTH - JIGSAW_WIDTH - 6;
    private static final int MIN_TARGET_Y = 5;
    private static final int MAX_TARGET_Y = BG_HEIGHT - JIGSAW_WIDTH - 5;

    private SlideCaptchaUtil() {
    }

    /**
     * 滑块验证码生成结果：背景图 / 拼图块 (base64 data URI) 与缺口坐标
     */
    public record SlideCaptchaImage(String bgImage, String jigsawImage, int targetX, int targetY) {
    }

    public static SlideCaptchaImage generate() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int targetX = random.nextInt(MIN_TARGET_X, MAX_TARGET_X + 1);
        int targetY = random.nextInt(MIN_TARGET_Y, MAX_TARGET_Y + 1);

        Area shape = buildPieceShape(targetX, targetY);
        BufferedImage bg = paintBackground(random);
        // 先从干净的背景上裁出拼图块, 再挖缺口; 顺序反了拼图块会裁到暗色缺口而非背景纹理
        BufferedImage jigsaw = extractJigsaw(bg, shape, targetX);
        punchHole(bg, shape);

        return new SlideCaptchaImage(toBase64DataUri(bg), toBase64DataUri(jigsaw), targetX, targetY);
    }

    /**
     * 拼图轮廓：圆角方形主体 + 右侧与底部凸扣 (经典拼图造型)
     */
    private static Area buildPieceShape(int x, int y) {
        int s = PIECE_BODY_SIZE;
        int knob = 13;
        Area area = new Area(new RoundRectangle2D.Float(x, y, s, s, 10, 10));
        area.add(new Area(new Ellipse2D.Float(x + s - 4, y + s / 2f - knob / 2f, knob, knob)));
        area.add(new Area(new Ellipse2D.Float(x + s / 2f - knob / 2f, y + s - 4, knob, knob)));
        return area;
    }

    /**
     * 程序化背景：同色系双色渐变 + 随机半透明几何图形 + 轻噪点
     */
    private static BufferedImage paintBackground(ThreadLocalRandom random) {
        BufferedImage img = new BufferedImage(BG_WIDTH, BG_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        float hue = random.nextFloat();
        g.setPaint(new GradientPaint(0, 0,
                Color.getHSBColor(hue, 0.42f, 0.90f),
                BG_WIDTH, BG_HEIGHT,
                Color.getHSBColor((hue + 0.10f) % 1.0f, 0.40f, 0.68f)));
        g.fillRect(0, 0, BG_WIDTH, BG_HEIGHT);

        int shapeCount = 12 + random.nextInt(6);
        for (int i = 0; i < shapeCount; i++) {
            float shapeHue = (hue + random.nextFloat() * 0.16f - 0.06f + 1.0f) % 1.0f;
            int rgb = Color.HSBtoRGB(shapeHue, 0.45f, random.nextBoolean() ? 0.95f : 0.55f);
            g.setColor(new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, 25 + random.nextInt(40)));

            int w = 30 + random.nextInt(120);
            int h = 20 + random.nextInt(90);
            int px = random.nextInt(BG_WIDTH) - w / 2;
            int py = random.nextInt(BG_HEIGHT) - h / 2;
            switch (random.nextInt(4)) {
                case 0 -> g.fillOval(px, py, w, h);
                case 1 -> {
                    g.setStroke(new BasicStroke(3 + random.nextInt(5)));
                    g.drawOval(px, py, w, h);
                }
                case 2 -> {
                    g.setStroke(new BasicStroke(2 + random.nextInt(4)));
                    g.drawLine(px, py, px + w, py + random.nextInt(BG_HEIGHT));
                }
                default -> g.fillRoundRect(px, py, w, h, 24, 24);
            }
        }

        for (int i = 0; i < 500; i++) {
            g.setColor(new Color(255, 255, 255, random.nextInt(30)));
            g.fillRect(random.nextInt(BG_WIDTH), random.nextInt(BG_HEIGHT), 1, 1);
        }
        g.dispose();
        return img;
    }

    /**
     * 背景图挖缺口：先画偏移投影再画半透明暗色缺口，拼图块对齐后形成"填回"效果
     */
    private static void punchHole(BufferedImage bg, Area shape) {
        Graphics2D g = bg.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.translate(2, 2);
        g.setColor(new Color(0, 0, 0, 50));
        g.fill(shape);
        g.translate(-2, -2);
        g.setColor(new Color(0, 0, 0, 110));
        g.fill(shape);
        g.dispose();
    }

    /**
     * 从背景图裁剪拼图块：平移画布使缺口区域落到画布 x=0 处，保留真实 y 位置并加描边
     */
    private static BufferedImage extractJigsaw(BufferedImage bg, Area shape, int targetX) {
        BufferedImage piece = new BufferedImage(JIGSAW_WIDTH, BG_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = piece.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.translate(-targetX, 0);
        g.setClip(shape);
        g.drawImage(bg, 0, 0, null);
        g.setClip(null);
        g.setStroke(new BasicStroke(2.4f));
        g.setColor(new Color(255, 255, 255, 215));
        g.draw(shape);
        g.setStroke(new BasicStroke(1.0f));
        g.setColor(new Color(0, 0, 0, 90));
        g.draw(shape);
        g.dispose();
        return piece;
    }

    private static String toBase64DataUri(BufferedImage image) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("滑块验证码图片编码失败", e);
        }
    }
}
