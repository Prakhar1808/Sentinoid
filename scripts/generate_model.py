#!/usr/bin/env python3
"""
Generate a placeholder TFLite model for Sentinoid using PyTorch.
"""

import torch
import torch.nn as nn
import torch.optim as optim
import numpy as np


class MalwareClassifier(nn.Module):
    def __init__(self, input_features=20):
        super(MalwareClassifier, self).__init__()
        self.fc1 = nn.Linear(input_features, 32)
        self.fc2 = nn.Linear(32, 16)
        self.fc3 = nn.Linear(16, 1)
        self.dropout = nn.Dropout(0.3)

    def forward(self, x):
        x = torch.relu(self.fc1(x))
        x = self.dropout(x)
        x = torch.relu(self.fc2(x))
        x = self.dropout(x)
        x = torch.sigmoid(self.fc3(x))
        return x


def generate_model():
    print("[*] Generating placeholder TFLite model...")

    # Create model
    model = MalwareClassifier(20)
    model.eval()

    # Generate dummy data for testing
    print("[*] Generating synthetic training data...")
    np.random.seed(42)

    # Malware samples (label=1)
    malware_X = np.random.rand(1000, 20).astype(np.float32) * 0.5 + 0.3
    malware_y = np.ones((1000, 1), dtype=np.float32)

    # Benign samples (label=0)
    benign_X = np.random.rand(1000, 20).astype(np.float32) * 0.3
    benign_y = np.zeros((1000, 1), dtype=np.float32)

    # Combine
    X = np.vstack([malware_X, benign_X])
    y = np.vstack([malware_y, benign_y])

    # Train briefly
    print("[*] Training model...")
    X_tensor = torch.from_numpy(X)
    y_tensor = torch.from_numpy(y)

    dataset = torch.utils.data.TensorDataset(X_tensor, y_tensor)
    loader = torch.utils.data.DataLoader(dataset, batch_size=32, shuffle=True)

    optimizer = optim.Adam(model.parameters(), lr=0.001)
    criterion = nn.BCELoss()

    for epoch in range(20):
        for batch_X, batch_y in loader:
            optimizer.zero_grad()
            output = model(batch_X)
            loss = criterion(output, batch_y)
            loss.backward()
            optimizer.step()

    print("[+] Model training complete")

    # Export to ONNX
    print("[*] Exporting to ONNX...")
    onnx_path = "/home/prakhar/Documents/GitHub/Sentinoid/app/src/main/assets/malware_model.onnx"

    dummy_input = torch.zeros(1, 20)
    torch.onnx.export(
        model,
        dummy_input,
        onnx_path,
        input_names=["input"],
        output_names=["output"],
        dynamic_axes={"input": {0: "batch_size"}, "output": {0: "batch_size"}},
    )
    print(f"[+] ONNX model saved to: {onnx_path}")

    # Convert to TFLite using onnx-tf
    print("[*] Converting to TFLite...")
    try:
        import onnx
        from onnx_tf import backend

        onnx_model = onnx.load(onnx_path)
        tf_rep = backend.prepare(onnx_model)

        # Save as SavedModel
        saved_model_path = (
            "/home/prakhar/Documents/GitHub/Sentinoid/app/src/main/assets/saved_model"
        )
        tf_rep.export_graph(saved_model_path)

        # Convert to TFLite
        import tensorflow as tf

        converter = tf.lite.TFLiteConverter.from_saved_model(saved_model_path)
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        tflite_model = converter.convert()

        tflite_path = "/home/prakhar/Documents/GitHub/Sentinoid/app/src/main/assets/malware_model.tflite"
        with open(tflite_path, "wb") as f:
            f.write(tflite_model)

        print(f"[+] TFLite model saved to: {tflite_path}")
        print(f"    Model size: {len(tflite_model) / 1024:.2f} KB")

    except Exception as e:
        print(f"[!] TFLite conversion failed: {e}")
        print("[*] Trying alternative approach...")

        # Alternative: save as ONNX only for now
        print(f"[+] ONNX model available at: {onnx_path}")

        # Create a simple placeholder file
        tflite_path = "/home/prakhar/Documents/GitHub/Sentinoid/app/src/main/assets/malware_model.tflite"

        # Try using tf.compat.v1 if available
        try:
            import tensorflow as tf

            # Try tf.lite as fallback
            print("[*] Attempting direct TFLite conversion...")
        except:
            pass


if __name__ == "__main__":
    generate_model()
