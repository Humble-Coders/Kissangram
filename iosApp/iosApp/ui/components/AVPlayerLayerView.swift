import SwiftUI
import AVFoundation

/// Lightweight video display using AVPlayerLayer instead of AVPlayerViewController.
/// Teardown is cheap when the view is removed during scroll—fixes the hang when
/// the last video leaves the screen. Used for feed videos (Instagram-style).
struct AVPlayerLayerView: UIViewRepresentable {
    let player: AVPlayer
    
    func makeUIView(context: Context) -> PlayerLayerHostView {
        let view = PlayerLayerHostView()
        view.playerLayer.player = player
        view.playerLayer.videoGravity = .resizeAspectFill
        return view
    }
    
    func updateUIView(_ uiView: PlayerLayerHostView, context: Context) {
        uiView.playerLayer.player = player
    }
}

/// UIView that hosts an AVPlayerLayer. Uses layerClass so the layer is created
/// automatically and its frame follows the view's bounds.
final class PlayerLayerHostView: UIView {
    override class var layerClass: AnyClass { AVPlayerLayer.self }
    
    var playerLayer: AVPlayerLayer { layer as! AVPlayerLayer }
    
    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .black
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
}
