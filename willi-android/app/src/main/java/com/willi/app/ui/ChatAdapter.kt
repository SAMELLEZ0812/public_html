package com.willi.app.ui

import android.graphics.Color
import android.view.*
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.willi.app.R
import com.willi.app.data.models.ChatMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatAdapter(private val messages: List<ChatMessage>) :
    RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {

    companion object {
        const val VIEW_USER = 0
        const val VIEW_WILLI = 1
    }

    override fun getItemViewType(position: Int) =
        if (messages[position].isWilli) VIEW_WILLI else VIEW_USER

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layoutRes = if (viewType == VIEW_WILLI) R.layout.item_message_willi
                        else R.layout.item_message_user
        val view = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemCount() = messages.size

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvText: TextView = itemView.findViewById(R.id.tvMessageText)
        private val tvMeta: TextView? = itemView.findViewById(R.id.tvMessageMeta)
        private val sdf = SimpleDateFormat("HH:mm", Locale.FRANCE)

        fun bind(message: ChatMessage) {
            tvText.text = message.text

            if (message.isWilli) {
                val confPct = (message.confidence * 100).toInt()
                tvMeta?.text = "${message.emotion.uppercase()} • ${confPct}% • ${sdf.format(Date(message.timestamp))}"
                tvMeta?.setTextColor(Color.parseColor("#CC1122"))
            } else {
                tvMeta?.text = sdf.format(Date(message.timestamp))
                tvMeta?.setTextColor(Color.parseColor("#886666"))
            }
        }
    }
}
