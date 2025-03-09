# RareSlideViewHelper
本项目隶属于GenOuka的Rare手表基础设施开源项目计划。

这是一个用于Rare系列软件的一个通用的视图滑动工具类。

## 使用示例
```java
// 为ImageView附加手势功能
new AdvancedGestureHelper.Builder(imageView)
    .consumeTouch(false)    // 不拦截原有点击事件
    .dragThreshold(8)       // 8dp触发拖动
    .damping(0.9f)         // 自定义阻尼系数
    .attach();

// 解绑手势功能
AdvancedGestureHelper.detach(imageView);
```

## 许可证
采用MIT协议，详见LICENSE文件。
