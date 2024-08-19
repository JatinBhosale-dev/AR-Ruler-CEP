<h1 align="center">
  KLAR Ruler App
</h1>

# What is the idea?

This is an Augmented Reality Project in which we have made a Measurement App using Android ARCore.

# What is the Augmented Reality?

Augmented Reality (AR) is a technology that overlays digital content, such as images, sounds, and information, onto the real world through devices like smartphones or tablets.

# What is ARCore?

ARCore is a software development kit (SDK) developed by Google that allows developers to build augmented reality (AR) applications for Android devices.

# 🚀  Prerequisites
  **ARCore supported Android Device**

🎯**KLAR Ruler App Usage**

- Move your device inorder to detect a Plane or Depth Point correctly.
- Upon recognizing that the cursor which was a sphere(3D) is now a circle(2D) press the anchor button to drop and anchor.
- Once the anchor is dropped, drop the second anchor on another point in screen if a circle(2D) is available.
- Upon successful dropping of two anchors in ARScene we can find distance between them.

# 💻 Android Developement (For developers only)

- Android Studio Koala | 2024
- minSdk = 24
- targetSdk = 34
- gradle library -> com.google.ar:core
- gradle library -> com.google.ar.sceneform:core
- gradle library -> com.google.ar.sceneform.ux:sceneform-ux


# 💻 Algorithm (How it works <For developers only>)

 The main heroes in the whole process are the HitResults for the an Anchor detection. The app continously looks around for HitResults and if there are any results 
 the Circle(2D) will prompt to drop the anchor. Once we drop the second anchor aswell we compute distance between them.

## 🗒️ Credits
**Kwanso Mobile Team**
