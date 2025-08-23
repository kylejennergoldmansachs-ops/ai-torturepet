using UnityEngine;

public class RagdollController : MonoBehaviour {
    void OnCollisionEnter(Collision collision) {
        string objName = collision.gameObject.name;
        float force = collision.relativeVelocity.magnitude;

        #if UNITY_ANDROID && !UNITY_EDITOR
        using (AndroidJavaClass jc = new AndroidJavaClass("com.unity3d.player.UnityPlayer")) {
            AndroidJavaObject act = jc.GetStatic<AndroidJavaObject>("currentActivity");
            act.Call("runOnUiThread", new AndroidJavaRunnable(() => {
                act.Call("onUnityEvent", objName, force);
            }));
        }
        #endif
    }
}
