import os
import torch
import torch.nn as nn
from torchvision import models, transforms
from PIL import Image
import numpy as np
from config import Config


class LightweightAnimalClassifier:
    def __init__(self):
        self.device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
        self.class_labels = Config.CLASS_LABELS
        self.confidence_threshold = Config.CONFIDENCE_THRESHOLD
        self.model = self._build_model()
        self.transform = self._build_transform()
        self._load_weights()

    def _build_model(self):
        model = models.mobilenet_v2(weights=None)
        num_features = model.classifier[1].in_features
        model.classifier = nn.Sequential(
            nn.Dropout(0.2),
            nn.Linear(num_features, 256),
            nn.ReLU(),
            nn.Dropout(0.3),
            nn.Linear(256, 3)
        )
        model = model.to(self.device)
        model.eval()
        return model

    def _build_transform(self):
        return transforms.Compose([
            transforms.Resize((224, 224)),
            transforms.ToTensor(),
            transforms.Normalize(
                mean=[0.485, 0.456, 0.406],
                std=[0.229, 0.224, 0.225]
            )
        ])

    def _load_weights(self):
        weights_path = Config.MODEL_WEIGHTS_PATH
        if os.path.exists(weights_path):
            state_dict = torch.load(weights_path, map_location=self.device)
            self.model.load_state_dict(state_dict)
            print(f"Model weights loaded from {weights_path}")
        else:
            print(f"Warning: Weights file not found at {weights_path}, using untrained model")
            print("In production, train the model and place weights at the specified path")

    def predict(self, image_input):
        if isinstance(image_input, str):
            image = Image.open(image_input).convert("RGB")
        elif isinstance(image_input, np.ndarray):
            image = Image.fromarray(cv2_bgr_to_rgb(image_input))
        elif isinstance(image_input, Image.Image):
            image = image_input.convert("RGB")
        else:
            raise ValueError("Unsupported image input type")

        input_tensor = self.transform(image).unsqueeze(0).to(self.device)

        with torch.no_grad():
            outputs = self.model(input_tensor)
            probabilities = torch.softmax(outputs, dim=1)
            max_prob, predicted_idx = torch.max(probabilities, 1)

        confidence = max_prob.item()
        class_idx = predicted_idx.item()
        class_name = self.class_labels[class_idx]

        return {
            "class_id": class_idx,
            "class_name": class_name,
            "confidence": confidence,
            "above_threshold": confidence >= self.confidence_threshold,
            "all_probabilities": {
                self.class_labels[i]: round(probabilities[0][i].item(), 4)
                for i in range(len(self.class_labels))
            }
        }


def cv2_bgr_to_rgb(bgr_image):
    return bgr_image[:, :, ::-1]
