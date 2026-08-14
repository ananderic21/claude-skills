package dev.anand.claudeskills.dto;

public record ProfilePicture(byte[] content, String contentType) {

    @Override
    public String toString() {
        return "ProfilePicture[contentType=" + contentType + ", bytes=" + content.length + "]";
    }
}
