package com.kwansolabs.ar_rulerapp


import android.annotation.SuppressLint
import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.core.Anchor
import com.google.ar.core.DepthPoint
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.InstantPlacementPoint
import com.google.ar.core.Plane
import com.google.ar.core.Point
import com.google.ar.core.Pose
import com.google.ar.core.Trackable
import com.google.ar.core.TrackingState
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.Scene
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.Color
import com.google.ar.sceneform.rendering.Material
import com.google.ar.sceneform.rendering.MaterialFactory
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.ShapeFactory
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.ar.sceneform.ux.ArFragment
import com.kwansolabs.klar.R
import kotlin.math.sqrt


class Measurement : AppCompatActivity(), Scene.OnUpdateListener,View.OnClickListener,OnItemSelectedListener {
    private lateinit var arFragment: ArFragment
    private lateinit var unitDropDown: Spinner
    private val units: Array<String> = arrayOf(UNIT_CENTIMETER, UNIT_METER, UNIT_FEET, UNIT_INCHES)
    private val cursorAnchor = mutableListOf<WrappedAnchor>()
    private lateinit var dockedAnchor: WrappedAnchor
    private var anchorRenderer: ModelRenderable? = null
    private var cursorRenderer: ModelRenderable? = null
    lateinit var anchorNode: AnchorNode
    var dockedAnchorNode: AnchorNode? = null
    lateinit var lineNode: Node
    var textNode: AnchorNode? = null
    lateinit var frame: Frame
    lateinit var hitResultList: List<HitResult>
    var firstHitResult: HitResult? = null
    var point1: Vector3?=null
    var point2: Vector3?=null
    var cp1: Float =0f
    var cp2: Float =0f
    var freezeAnchors = false
    var distanceCardViewRenderer: ViewRenderable? = null
    private var distanceWithUnits = ""
    private var distanceInMeters = 0f
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_measurement)
        arFragment = supportFragmentManager.findFragmentById(R.id.sceneform_fragment) as ArFragment
        arFragment.planeDiscoveryController?.hide()
        arFragment.planeDiscoveryController?.setInstructionView(null)
        arFragment.arSceneView?.scene?.addOnUpdateListener(this)
       // arFragment?.arSceneView?.planeRenderer?.isVisible = false
        findViewById<ImageView>(R.id.dockButton)?.setOnClickListener(this)
        findViewById<ImageView>(R.id.lockButton)?.setOnClickListener(this)
        findViewById<ImageView>(R.id.clearButton)?.setOnClickListener(this)
        cp1 = this.resources.displayMetrics.widthPixels.toFloat()/2
        cp2 = this.resources.displayMetrics.heightPixels.toFloat()/2



        unitDropDown = findViewById(R.id.unit_selector)!!
        val adapter: ArrayAdapter<String> = ArrayAdapter<String>(this,
            R.layout.spinner_main, units
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        unitDropDown.setAdapter(adapter)
        unitDropDown.onItemSelectedListener = this



        findViewById<ImageView>(R.id.lockButton).isEnabled = false
        findViewById<ImageView>(R.id.lockButton).setBackgroundResource(R.drawable.bg_button_disable)


        MaterialFactory.makeTransparentWithColor(
            this,
            Color(android.graphics.Color.WHITE)
        )
            .thenAccept { material: Material? ->
                anchorRenderer = ShapeFactory.makeCylinder(
                    0.035f,
                          0.001f,
                    Vector3.zero(),
                    material)
                anchorRenderer!!.isShadowCaster = false
                anchorRenderer!!.isShadowReceiver = false
            }
            .exceptionally {
                val builder = AlertDialog.Builder(this)
                builder.setMessage(it.message).setTitle("Error")
                val dialog = builder.create()
                dialog.show()
                return@exceptionally null
            }

        MaterialFactory.makeTransparentWithColor(
            this,
            Color(android.graphics.Color.WHITE)
        )
            .thenAccept { material: Material? ->
                cursorRenderer = ShapeFactory.makeSphere(
                    0.055f,
                    Vector3.zero(),
                    material)
                cursorRenderer!!.isShadowCaster = false
                cursorRenderer!!.isShadowReceiver = false
            }
            .exceptionally {
                val builder = AlertDialog.Builder(this)
                builder.setMessage(it.message).setTitle("Error")
                val dialog = builder.create()
                dialog.show()
                return@exceptionally null
            }

        ViewRenderable
            .builder()
            .setView(this, R.layout.text_layout)
            .build()
            .thenAccept{
                distanceCardViewRenderer = it
                distanceCardViewRenderer!!.isShadowCaster = false
                distanceCardViewRenderer!!.isShadowReceiver = false
            }
            .exceptionally {
                val builder = AlertDialog.Builder(this)
                builder.setMessage(it.message).setTitle("Error")
                val dialog = builder.create()
                dialog.show()
                return@exceptionally null
            }

    }

    @SuppressLint("ResourceType")
    override fun onUpdate(p0: FrameTime?) {
        System.gc()
        runOnUiThread {
            frame = arFragment.arSceneView?.session?.update()!!
            if (!freezeAnchors && frame.camera.trackingState == TrackingState.TRACKING) {

                hitResultList = frame.hitTest(cp1, cp2)
                firstHitResult =
                    hitResultList.firstOrNull { hit ->
                        when (val trackable = hit.trackable!!) {
                            is Plane -> trackable.isPoseInPolygon(hit.hitPose) &&
                                    calculateDistanceToPlane(hit.hitPose, frame.camera.pose) > 0

                            is Point -> trackable.orientationMode == Point.OrientationMode.ESTIMATED_SURFACE_NORMAL
                            is InstantPlacementPoint -> false
                            is DepthPoint -> true
                            else -> false
                        }
                    }

                if (firstHitResult != null) {
                    if (cursorAnchor.size >= 1) {
                        //cursorAnchor[0].anchor.detach()
                        cursorAnchor.removeAt(0)
                        arFragment.arSceneView.scene.removeChild(anchorNode)
                    }
                    try {
                        cursorAnchor.add(
                            WrappedAnchor(
                                firstHitResult!!.createAnchor(),
                                firstHitResult!!.trackable
                            )
                        )
                    } catch (_: Exception) {
                        return@runOnUiThread
                    }

                    anchorNode = AnchorNode(cursorAnchor[0].anchor)
                    anchorNode.renderable = anchorRenderer
                    anchorNode.setParent(arFragment.arSceneView.scene)
                    Log.e("HitResultList", anchorNode.anchor?.pose.toString())


                    if (dockedAnchorNode != null) {
                        point1 = anchorNode.worldPosition
                        point2 = dockedAnchorNode!!.worldPosition
                        val difference = Vector3.subtract(point1, point2)
                        val directionFromTopToBottom = difference.normalized()
                        val rotationFromAToB =
                            Quaternion.lookRotation(directionFromTopToBottom, Vector3.up())
                        MaterialFactory.makeOpaqueWithColor(
                            applicationContext,
                            Color(android.graphics.Color.WHITE)
                        )
                            .thenAccept { material: Material? ->
                                val model = ShapeFactory.makeCube(
                                    Vector3(.005f, .00f, difference.length()),
                                    Vector3.zero(), material
                                )
                                model!!.isShadowCaster = false
                                model.isShadowReceiver = false
                                if (::lineNode.isInitialized) {
                                    dockedAnchorNode!!.removeChild(lineNode)
                                }
                                lineNode = Node()
                                lineNode.setParent(dockedAnchorNode)
                                lineNode.renderable = model
                                lineNode.worldPosition = Vector3.add(point1, point2).scaled(.5f)
                                lineNode.worldRotation = rotationFromAToB

                                try {
                                    if (::dockedAnchor.isInitialized) {
                                        distanceInMeters = computeDistance(
                                            dockedAnchor.anchor,
                                            cursorAnchor[0].anchor
                                        )
                                        distanceWithUnits = convertUnits(distanceInMeters)
                                        findViewById<TextView>(R.id.distanceMeter).text =
                                            distanceWithUnits
                                    }


                                } catch (e: InterruptedException) {
                                    e.printStackTrace()
                                }
                            }
                    }
                } else {
                    if (cursorAnchor.size >= 1) {
                        //cursorAnchor[0].anchor.detach()
                        cursorAnchor.removeAt(0)
                        arFragment.arSceneView.scene.removeChild(anchorNode)
                    }
                    cursorAnchor.add(
                        WrappedAnchor(
                            arFragment.arSceneView?.session!!.createAnchor(
                                frame.getCamera().getPose()
                                    .compose(Pose.makeTranslation(0f, 0f, -1f))
                                    .extractTranslation()
                            ),
                            null
                        )
                    )

                    anchorNode = AnchorNode(cursorAnchor[0].anchor)
                    anchorNode.renderable = cursorRenderer
                    anchorNode.setParent(arFragment.arSceneView.scene)
                }
            }
            else if(freezeAnchors)
            {
                /**** for text in ****/
                if (textNode==null) {
                    Log.e("Anchor Pose",anchorNode.anchor!!.pose.toString())
                    val distanceAnchorPose = anchorNode.anchor!!.pose
                    textNode = AnchorNode(arFragment.arSceneView?.session!!.createAnchor(
                        distanceAnchorPose
                                .compose(Pose.makeRotation(0f, distanceAnchorPose.qy(), distanceAnchorPose.qz(),distanceAnchorPose.qw()))
                            .extractTranslation()
                    ))
                    textNode!!.renderable = distanceCardViewRenderer
                    textNode!!.setParent(arFragment.arSceneView.scene)
                    (distanceCardViewRenderer!!.view).findViewById<TextView>(R.id.pointCard).text = distanceWithUnits.plus(unitDropDown.selectedItem)
                }

                /**** for text in ar ****/
            }
        }
    }

    private fun computeDistance(anchor1: Anchor,anchor2: Anchor):Float
    {
        val dx: Float = anchor1.pose.tx() - anchor2.pose.tx()
        val dy: Float = anchor1.pose.ty() - anchor2.pose.ty()
        val dz: Float = anchor1.pose.tz() - anchor2.pose.tz()
        val distanceMeters =
            sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()

        return distanceMeters
    }

    @SuppressLint("DefaultLocale")
    private fun convertUnits(distanceMeters: Float):String
    {
        when (unitDropDown.selectedItem) {
            UNIT_CENTIMETER -> {
                return String.format("%.2f", (distanceMeters*100))
            }
            UNIT_METER -> {
                return String.format("%.2f",(distanceMeters))
            }
            UNIT_FEET -> {
                return String.format("%.2f",(distanceMeters * 3.280))
            }
            UNIT_INCHES -> {
                return String.format("%.2f",(distanceMeters*39.26))
            }
        }

        return "Error while converting units"
    }

    private fun calculateDistanceToPlane(planePose: Pose, cameraPose: Pose): Float {
        val normal = FloatArray(3)
        val cameraX = cameraPose.tx()
        val cameraY = cameraPose.ty()
        val cameraZ = cameraPose.tz()
        planePose.getTransformedAxis(1, 1.0f, normal, 0)
        return (cameraX - planePose.tx()) * normal[0] + ((cameraY - planePose.ty()) * normal[1]
                ) + ((cameraZ - planePose.tz()) * normal[2])
    }

    private data class WrappedAnchor(
        val anchor: Anchor,
        val trackable: Trackable?,
    )

    override fun onClick(v: View?) {
        if (v?.id == R.id.dockButton && firstHitResult!=null) {
            if (dockedAnchorNode!=null) {
                arFragment.arSceneView.scene.removeChild(dockedAnchorNode)
            }
            dockedAnchor = cursorAnchor[0]
            dockedAnchorNode = AnchorNode(cursorAnchor[0].anchor)
            dockedAnchorNode!!.renderable = anchorRenderer
            dockedAnchorNode!!.setParent(arFragment.arSceneView.scene)
            findViewById<ImageView>(R.id.lockButton).isEnabled = true
            findViewById<ImageView>(R.id.lockButton).setBackgroundResource(R.drawable.bg_buttons)
            findViewById<LinearLayout>(R.id.distanceLabel).visibility = View.VISIBLE
        }
        if (v?.id == R.id.lockButton) {
          freezeAnchors=true
            findViewById<ImageView>(R.id.lockButton).isEnabled = false
            findViewById<ImageView>(R.id.lockButton).setBackgroundResource(R.drawable.bg_button_disable)
            findViewById<ImageView>(R.id.dockButton).isEnabled = false
            findViewById<ImageView>(R.id.dockButton).setBackgroundResource(R.drawable.bg_button_disable)
        }
        if (v?.id == R.id.clearButton) {
            freezeAnchors = false
            if (dockedAnchorNode!=null) {
                arFragment.arSceneView.scene.removeChild(dockedAnchorNode)
                if(textNode!=null) arFragment.arSceneView.scene.removeChild(textNode)
                dockedAnchorNode = null
                textNode = null
            }
            findViewById<ImageView>(R.id.lockButton).isEnabled = false
            findViewById<ImageView>(R.id.lockButton).setBackgroundResource(R.drawable.bg_button_disable)
            findViewById<ImageView>(R.id.dockButton).isEnabled = true
            findViewById<ImageView>(R.id.dockButton).setBackgroundResource(R.drawable.bg_buttons)
            distanceInMeters = 0f
            distanceWithUnits = ""
            findViewById<LinearLayout>(R.id.distanceLabel).visibility = View.GONE
        }
    }

    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        if(distanceInMeters>0f) {
            distanceWithUnits = convertUnits(distanceInMeters)
                findViewById<TextView>(R.id.distanceMeter).text =
                    distanceWithUnits
            if(freezeAnchors)
            {
                (distanceCardViewRenderer!!.view).findViewById<TextView>(R.id.pointCard).text = distanceWithUnits.plus(unitDropDown.selectedItem)
            }
        }
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {

        /**
         * Nothing to implement
         */

    }


}