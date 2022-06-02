package com.example.drawingapp

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View

class DrawingView(context : Context, attrs : AttributeSet) : View(context, attrs) {

    // VARIABLES
    private var canvas : Canvas? = null
    private var myDrawPaint : Paint? = null
    private var myCanvasPaint : Paint? = null
    private var myDrawPath : CustomPath? = null
    private var myCanvasBitmap : Bitmap? = null
    private val myPaths = ArrayList<CustomPath>()
    private val myUndoPaths = ArrayList<CustomPath>()
    //// initialized variables
    private var color = Color.BLACK
    private var myBrushSize : Float = 0.toFloat()

    init {
        setupDrawing()
    }

    // remove last line from myPath and stores in myUndoPaths
    // .removeAt = removes element[i] and returns it
    fun onClickUndo(){
        if(myPaths.size > 0){
            myUndoPaths.add(myPaths.removeAt(myPaths.size - 1))
            invalidate()
        }
    }

    // removes last line from myUndoPath to myPaths
    // .removeAt = removes element[i] and returns it
    fun onClickRedo(){
        if (myUndoPaths.size > 0){
            myPaths.add(myUndoPaths.removeAt(myUndoPaths.size - 1))
            invalidate()
        }
    }

    private fun setupDrawing() {
        // initialize variables
        //// paint (brush)
        myDrawPaint = Paint()
        myDrawPaint!!.color = color
        myDrawPaint!!.style = Paint.Style.STROKE
        myDrawPaint!!.strokeJoin = Paint.Join.ROUND
        myDrawPaint!!.strokeCap = Paint.Cap.ROUND
        //// path (stroke)
        myDrawPath = CustomPath(color, myBrushSize)
        myCanvasPaint = Paint(Paint.DITHER_FLAG)
    }

    // screen size
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        myCanvasBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        canvas = Canvas(myCanvasBitmap!!)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawBitmap(myCanvasBitmap!!, 0f, 0f, myCanvasPaint)

        // keeps path in screen storing them in an arraylist
        for (path in myPaths) {
            myDrawPaint!!.strokeWidth = path.brushThickness
            myDrawPaint!!.color = path.color

            canvas.drawPath(path, myDrawPaint!!)
        }

        // draw on screen
        if (!myDrawPath!!.isEmpty) {
            myDrawPaint!!.strokeWidth = myDrawPath!!.brushThickness
            myDrawPaint!!.color = myDrawPath!!.color

            canvas.drawPath(myDrawPath!!, myDrawPaint!!)
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        // coordinates
        val touchX = event?.x
        val touchY = event?.y

        // touch, drag, release
        when(event?.action) {
            MotionEvent.ACTION_DOWN -> {
                myDrawPath!!.color = color
                myDrawPath!!.brushThickness = myBrushSize

                myDrawPath!!.reset()
                myDrawPath!!.moveTo(touchX!!, touchY!!)
            }
            MotionEvent.ACTION_MOVE -> {
                myDrawPath!!.lineTo(touchX!!, touchY!!)
            }
            MotionEvent.ACTION_UP -> {
                myPaths.add(myDrawPath!!)
                myDrawPath = CustomPath(color, myBrushSize)
            }
            else -> return false
        }
        invalidate()

        return true
    }

    fun onBrushSizeChange(newSize :  Float){
        myBrushSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, newSize, resources.displayMetrics)
        myDrawPaint!!.strokeWidth = myBrushSize
    }

    fun setColor(newColor : String){
        color = Color.parseColor(newColor)

        myDrawPaint!!.color = color
    }

    internal inner class CustomPath(var color : Int,
                                    var brushThickness : Float) : Path()
}