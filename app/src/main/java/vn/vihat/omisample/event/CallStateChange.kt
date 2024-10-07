package vn.vihat.omisample.event

enum class OmiCallState(val value: Int) {
    calling(0),
    early(1),
    connecting(2),
    confirmed(3),
    incoming(4),
    disconnected(5),
}