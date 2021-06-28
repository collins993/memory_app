package io.github.collins993.memoryapp.ui

import android.animation.ArgbEvaluator
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.jinatonic.confetti.CommonConfetti
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.squareup.picasso.Picasso
import io.github.collins993.memoryapp.R
import io.github.collins993.memoryapp.adapters.MemoryBoardAdapter
import io.github.collins993.memoryapp.models.BoardSize
import io.github.collins993.memoryapp.models.MemoryGame
import io.github.collins993.memoryapp.models.UserImageList
import io.github.collins993.memoryapp.utils.EXTRA_BOARD_SIZE
import io.github.collins993.memoryapp.utils.EXTRA_GAME_NAME

class MainActivity : AppCompatActivity() {

    companion object{
        private const val TAG = "MainActivity"
        private const val CREATE_REQUEST_CODE = 200
    }

    private lateinit var clRoot : CoordinatorLayout
    private lateinit var memoryGame : MemoryGame
    private lateinit var rvBoard : RecyclerView
    private lateinit var tvNumMove : TextView
    private lateinit var tvNumPairs : TextView
    private lateinit var adapter : MemoryBoardAdapter
    private var customGameImages: List<String>? = null
    private val database = Firebase.firestore
    private var gameName: String? = null

    private var boardSize: BoardSize = BoardSize.EASY



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        clRoot = findViewById(R.id.clRoot)
        rvBoard = findViewById(R.id.rv_board)
        tvNumMove = findViewById(R.id.tv_num_moves)
        tvNumPairs = findViewById(R.id.tv_num_pairs)

//        val chosenImages = DEFAULT_ICONS.shuffled().take(boardSize.getNumPairs())
//        val randomizedImages = (chosenImages + chosenImages).shuffled()
//        val memoryCard = randomizedImages.map { MemoryCard(it) }

        setUpBoard()
    }

    private fun setUpBoard() {

        supportActionBar?.title = gameName ?: getString(R.string.app_name)
        when (boardSize) {
            BoardSize.EASY -> {
                tvNumMove.text = "Easy: 4 x 2"
                tvNumPairs.text = "Pairs: 0 / 4"
            }
            BoardSize.MEDIUM -> {
                tvNumMove.text = "Medium: 6 x 3"
                tvNumPairs.text = "Pairs: 0 / 9"
            }
            BoardSize.HARD -> {
                tvNumMove.text = "Easy: 6 x 4"
                tvNumPairs.text = "Pairs: 0 / 12"
            }
        }
        tvNumPairs.setTextColor(ContextCompat.getColor(this, R.color.color_progress_none))
        memoryGame = MemoryGame(boardSize, customGameImages)

        adapter = MemoryBoardAdapter(
            this,
            boardSize,
            memoryGame.cards,
            object : MemoryBoardAdapter.CardClickListener {
                override fun onCardClicked(position: Int) {
                    //Log.i(TAG, "cARD CLICKED $position")
                    updateGameWithFlip(position)
                }

            })
        rvBoard.adapter = adapter
        rvBoard.setHasFixedSize(true)
        rvBoard.layoutManager = GridLayoutManager(
            this,
            boardSize.getWidth(),

            )
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_item_refresh -> {
                //Set up game again
                if (memoryGame.getNumMoves() > 0 && !memoryGame.haveWonGame()) {
                    showAlertDialog("Quit your current game?", null, View.OnClickListener {
                        setUpBoard()
                    })
                }
                else{
                    setUpBoard()
                }

                return true
            }
            R.id.menu_item_new_size -> {
                showNewSizeDialog()
                return true
            }
            R.id.menu_item_custom -> {
                showCreationDialog()
                return true
            }
            R.id.menu_item_download -> {
                showDownloadDialog()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showDownloadDialog() {
        val boardDownloadView = LayoutInflater.from(this).inflate(R.layout.dialog_download_board, null)
        showAlertDialog("Fetch memory game", boardDownloadView, View.OnClickListener {
            //Grab te text of the game name that the user wants to download
            val etDownloadGame = boardDownloadView.findViewById<EditText>(R.id.et_download_game)
            val gameToDownload = etDownloadGame.text.toString().trim()
            downloadGame(gameToDownload)
        })
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == CREATE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            val customGameName = data?.getStringExtra(EXTRA_GAME_NAME)
            if (customGameName == null) {
                Log.e(TAG, "Got null custom game from CreateActivity")
                return
            }
            downloadGame(customGameName)
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun downloadGame(customGameName: String) {
        database.collection("games").document(customGameName).get().addOnSuccessListener { document ->

            val userImageList = document.toObject(UserImageList::class.java)
            if (userImageList?.images == null) {
                Log.e(TAG, "Invalid custom game data fro Firestore")
                Snackbar.make(clRoot, "Sorry we couldn't find any such game, '$customGameName'", Snackbar.LENGTH_LONG).show()
                return@addOnSuccessListener
            }
            val numCards = userImageList.images.size * 2
            boardSize = BoardSize.getByValue(numCards)
            customGameImages = userImageList.images
            for (imageUrl in userImageList.images) {
                Picasso.get().load(imageUrl).fetch()
            }
            Snackbar.make(clRoot, "You're now playing '$customGameName'!", Snackbar.LENGTH_LONG).show()
            gameName = customGameName
            setUpBoard()



        }.addOnFailureListener { exception ->
            Log.e(TAG, "Exception when retrieving game", exception)

        }
    }

    private fun showCreationDialog() {
        val boardSizeView = LayoutInflater.from(this).inflate(R.layout.dialog_board_size, null)
        val radioGroupSize = boardSizeView.findViewById<RadioGroup>(R.id.radio_group)

        showAlertDialog("Create your own memory board", boardSizeView, View.OnClickListener {
            //Set a new value for board size
            val desiredBoardSize = when(radioGroupSize.checkedRadioButtonId){
                R.id.rv_easy ->BoardSize.EASY
                R.id.rv_medium ->BoardSize.MEDIUM
                else -> BoardSize.HARD
            }

            // Navigate new screen
            val intent = Intent(this, CreateActivity::class.java)
            intent.putExtra(EXTRA_BOARD_SIZE, desiredBoardSize)
            startActivityForResult(intent, CREATE_REQUEST_CODE)
        })
    }

    private fun showNewSizeDialog() {
        val boardSizeView = LayoutInflater.from(this).inflate(R.layout.dialog_board_size, null)
        val radioGroupSize = boardSizeView.findViewById<RadioGroup>(R.id.radio_group)

        when(boardSize) {
            BoardSize.EASY -> radioGroupSize.check(R.id.rv_easy)
            BoardSize.MEDIUM -> radioGroupSize.check(R.id.rv_medium)
            BoardSize.HARD -> radioGroupSize.check(R.id.rv_hard)
        }
        showAlertDialog("Choose new size", boardSizeView, View.OnClickListener {
            //Set a new value for board size
            boardSize = when(radioGroupSize.checkedRadioButtonId){
                R.id.rv_easy ->BoardSize.EASY
                R.id.rv_medium ->BoardSize.MEDIUM
                else -> BoardSize.HARD
            }
            gameName = null
            customGameImages = null
            setUpBoard()
        })
    }

    private fun showAlertDialog(title: String, view: View?, positiveClickListener: View.OnClickListener) {
        AlertDialog.Builder(this).
                setTitle(title)
            .setView(view)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("OK"){ _,_  ->
                positiveClickListener.onClick(null)
            }.show()
    }

    private fun updateGameWithFlip(position: Int) {
        //Error checking
        if (memoryGame.haveWonGame()) {
            // Alert the user of invalid move
                Snackbar.make(clRoot, "You already won!", Snackbar.LENGTH_LONG).show()
            return
        }
        if (memoryGame.isCardFaceUp(position)) {
            //Alert the user of an invalid move
            Snackbar.make(clRoot, "Invalid move!", Snackbar.LENGTH_SHORT).show()
            return
        }
        //Actually flip over the card
        if(memoryGame.flipCard(position)){
            val color = ArgbEvaluator().evaluate(
                memoryGame.numPairsFound.toFloat() / boardSize.getNumPairs(),
                ContextCompat.getColor(this, R.color.color_progress_none),
                ContextCompat.getColor(this, R.color.color_progress_full)
            ) as Int
            tvNumPairs.setTextColor(color)
            Log.i(TAG, "Found a match! Num pairs found: ${memoryGame.numPairsFound}")
            tvNumPairs.text = "Paris: ${memoryGame.numPairsFound} / ${boardSize.getNumPairs()}"
            if (memoryGame.haveWonGame()) {
                Snackbar.make(clRoot, "You have won! Congratulations.", Snackbar.LENGTH_LONG).show()
                CommonConfetti.rainingConfetti(clRoot, intArrayOf(Color.YELLOW, Color.RED, Color.MAGENTA)).oneShot()
            }
        }
        tvNumMove.text = "Moves: ${memoryGame.getNumMoves()}"
        adapter.notifyDataSetChanged()
    }
}