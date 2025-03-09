import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.PointF;
import android.util.Log;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public class AdvancedGestureHelper {
    private static final String TAG = "RareGestureHelper";
    private static final Map<View, AdvancedGestureHelper> sInstances = 
            Collections.synchronizedMap(new WeakHashMap<>());

    // Configuration
    private final boolean mConsumeTouch;
    private final long mScaleDelay;
    private final long mResetDelay;
    private final float mDampingFactor;
    private final float mDragThreshold;

    // Core components
    private final ScaleGestureDetector mScaleDetector;
    private final VelocityTracker mVelocityTracker;
    private final Handler mHandler = new Handler();
    private final WeakReference<View> mWeakView;
    private final PointF mScaleFocus = new PointF();
    private final TouchListenerProxy mTouchProxy;

    // State
    private int mCurrentMode = 0; // 0-none,1-drag,2-scale
    private float mLastRawX, mLastRawY;
    private ValueAnimator mCurrentAnimator;

    public static class Builder {
        private final View mView;
        private boolean mConsumeTouch = true;
        private long mScaleDelay = 1000;
        private long mResetDelay = 3000;
        private float mDamping = 0.95f;
        private float mDragThreshold = 5f;

        public Builder(View view) {
            this.mView = view;
        }

        public Builder consumeTouch(boolean consume) {
            mConsumeTouch = consume;
            return this;
        }

        public Builder scaleDelay(long delayMs) {
            mScaleDelay = delayMs;
            return this;
        }

        public Builder resetDelay(long delayMs) {
            mResetDelay = delayMs;
            return this;
        }

        public Builder damping(float factor) {
            mDamping = factor;
            return this;
        }

        public Builder dragThreshold(float dp) {
            Context ctx = mView.getContext();
            mDragThreshold = dp * ctx.getResources().getDisplayMetrics().density;
            return this;
        }

        public AdvancedGestureHelper attach() {
            AdvancedGestureHelper instance = new AdvancedGestureHelper(this);
            sInstances.put(mView, instance);
            return instance;
        }
    }

    private AdvancedGestureHelper(Builder builder) {
        // 初始化配置
        mConsumeTouch = builder.mConsumeTouch;
        mScaleDelay = builder.mScaleDelay;
        mResetDelay = builder.mResetDelay;
        mDampingFactor = builder.mDamping;
        mDragThreshold = builder.mDragThreshold;

        // 初始化核心组件
        mWeakView = new WeakReference<>(builder.mView);
        View target = builder.mView;
        Context context = target.getContext();
        mScaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        mVelocityTracker = VelocityTracker.obtain();

        // 代理原始触摸事件
        mTouchProxy = new TouchListenerProxy(target.getOnTouchListener());
        target.setOnTouchListener(mTouchProxy);
    }

    private class TouchListenerProxy implements View.OnTouchListener {
        private final View.OnTouchListener mOriginal;

        TouchListenerProxy(View.OnTouchListener original) {
            mOriginal = original;
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            boolean handled = handleGesture(event);
            boolean originalHandled = mOriginal != null && mOriginal.onTouch(v, event);
            return mConsumeTouch ? (handled || originalHandled) : (originalHandled || handled);
        }
    }

    private boolean handleGesture(MotionEvent event) {
        View view = mWeakView.get();
        if (view == null) return false;

        mScaleDetector.onTouchEvent(event);
        mVelocityTracker.addMovement(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                handleActionDown(view, event);
                return true;
            case MotionEvent.ACTION_MOVE:
                return handleActionMove(view, event);
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                handleActionUp(view);
                break;
            case MotionEvent.ACTION_POINTER_UP:
                if (event.getPointerCount() == 1) {
                    mCurrentMode = 0;
                }
                break;
        }
        return false;
    }

    private void handleActionDown(View view, MotionEvent event) {
        cancelAllTasks();
        mLastRawX = event.getRawX();
        mLastRawY = event.getRawY();

        mHandler.postDelayed(() -> mCurrentMode = 2, mScaleDelay);
        mHandler.postDelayed(() -> startResetAnimation(view), mResetDelay);
    }

    private boolean handleActionMove(View view, MotionEvent event) {
        if (mScaleDetector.isInProgress() || mCurrentMode == 2) return false;

        float dx = event.getRawX() - mLastRawX;
        float dy = event.getRawY() - mLastRawY;

        if (mCurrentMode == 0) {
            if (Math.abs(dx) > mDragThreshold || Math.abs(dy) > mDragThreshold) {
                mCurrentMode = 1;
                mHandler.removeCallbacksAndMessages(null);
            } else {
                return false;
            }
        }

        if (mCurrentMode == 1) {
            view.setTranslationX(view.getTranslationX() + dx);
            view.setTranslationY(view.getTranslationY() + dy);
            mLastRawX = event.getRawX();
            mLastRawY = event.getRawY();
            return true;
        }
        return false;
    }

    private void handleActionUp(View view) {
        cancelAllTasks();
        if (mCurrentMode == 1) {
            applyInertialScroll(view);
        }
        mCurrentMode = 0;
    }

    private void applyInertialScroll(final View view) {
        mVelocityTracker.computeCurrentVelocity(1000);
        final float[] velocity = {
            mVelocityTracker.getXVelocity(),
            mVelocityTracker.getYVelocity()
        };

        mCurrentAnimator = ValueAnimator.ofFloat(0, 1).setDuration(1500);
        mCurrentAnimator.setInterpolator(new DecelerateInterpolator());
        mCurrentAnimator.addUpdateListener(anim -> {
            if (!view.isAttachedToWindow()) {
                anim.cancel();
                return;
            }

            velocity[0] *= mDampingFactor;
            velocity[1] *= mDampingFactor;
            view.setTranslationX(view.getTranslationX() + velocity[0] / 60f);
            view.setTranslationY(view.getTranslationY() + velocity[1] / 60f);
        });
        mCurrentAnimator.start();
    }

    private void startResetAnimation(View view) {
        if (!view.isAttachedToWindow()) return;

        final float startX = view.getTranslationX();
        final float startY = view.getTranslationY();
        final float startScaleX = view.getScaleX();
        final float startScaleY = view.getScaleY();

        mCurrentAnimator = ValueAnimator.ofFloat(0, 1).setDuration(500);
        mCurrentAnimator.addUpdateListener(anim -> {
            float fraction = anim.getAnimatedFraction();
            view.setTranslationX(startX * (1 - fraction));
            view.setTranslationY(startY * (1 - fraction));
            view.setScaleX(startScaleX + (1 - startScaleX) * fraction);
            view.setScaleY(startScaleY + (1 - startScaleY) * fraction);
        });
        mCurrentAnimator.start();
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            mHandler.removeCallbacksAndMessages(null);
            mScaleFocus.set(detector.getFocusX(), detector.getFocusY());
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            View view = mWeakView.get();
            if (view == null) return false;

            float scaleFactor = detector.getScaleFactor();
            float focusX = detector.getFocusX() - mScaleFocus.x;
            float focusY = detector.getFocusY() - mScaleFocus.y;

            view.setScaleX(view.getScaleX() * scaleFactor);
            view.setScaleY(view.getScaleY() * scaleFactor);
            view.setTranslationX(view.getTranslationX() + focusX);
            view.setTranslationY(view.getTranslationY() + focusY);
            return true;
        }
    }

    public static void detach(View view) {
        AdvancedGestureHelper helper = sInstances.get(view);
        if (helper != null) {
            helper.cleanup();
            sInstances.remove(view);
        }
    }

    private void cleanup() {
        cancelAllTasks();
        mVelocityTracker.recycle();
        View view = mWeakView.get();
        if (view != null) {
            view.setOnTouchListener(mTouchProxy.mOriginal);
        }
    }

    private void cancelAllTasks() {
        mHandler.removeCallbacksAndMessages(null);
        if (mCurrentAnimator != null) {
            mCurrentAnimator.cancel();
        }
    }
}
