"""
File utility functions for Bhasha Setu backend.
"""
import os
import asyncio
from typing import Optional
from utils.logger import get_logger

logger = get_logger(__name__)


async def safe_delete(filepath: str, delay: int = 1, max_retries: int = 3) -> bool:
    """
    Attempt to delete a file after a short delay to handle Windows file locking.
    
    Args:
        filepath: Path to the file to delete
        delay: Delay in seconds before attempting deletion
        max_retries: Maximum number of retry attempts
    
    Returns:
        True if file was deleted successfully, False otherwise
    """
    await asyncio.sleep(delay)
    
    for attempt in range(max_retries):
        try:
            if os.path.exists(filepath):
                os.remove(filepath)
                logger.debug(f"Successfully deleted file: {filepath}")
                return True
            else:
                logger.debug(f"File already deleted: {filepath}")
                return True
        except Exception as e:
            if attempt < max_retries - 1:
                logger.warning(f"Cleanup attempt {attempt + 1} failed for {filepath}: {e}. Retrying...")
                await asyncio.sleep(delay)
            else:
                logger.error(f"Failed to delete {filepath} after {max_retries} attempts: {e}")
                return False
    
    return False


def ensure_directory(directory: str) -> None:
    """
    Ensure a directory exists, creating it if necessary.
    
    Args:
        directory: Path to the directory
    """
    os.makedirs(directory, exist_ok=True)
    logger.debug(f"Ensured directory exists: {directory}")


def get_file_size(filepath: str) -> Optional[int]:
    """
    Get the size of a file in bytes.
    
    Args:
        filepath: Path to the file
    
    Returns:
        File size in bytes, or None if file doesn't exist
    """
    try:
        return os.path.getsize(filepath)
    except OSError:
        return None


def cleanup_old_files(directory: str, max_age_seconds: int = 3600) -> int:
    """
    Clean up old files in a directory.
    
    Args:
        directory: Directory to clean up
        max_age_seconds: Maximum age of files to keep in seconds
    
    Returns:
        Number of files deleted
    """
    import time
    
    if not os.path.exists(directory):
        return 0
    
    deleted_count = 0
    current_time = time.time()
    
    for filename in os.listdir(directory):
        filepath = os.path.join(directory, filename)
        
        if os.path.isfile(filepath):
            file_age = current_time - os.path.getmtime(filepath)
            
            if file_age > max_age_seconds:
                try:
                    os.remove(filepath)
                    deleted_count += 1
                    logger.debug(f"Deleted old file: {filepath} (age: {file_age:.0f}s)")
                except Exception as e:
                    logger.error(f"Failed to delete old file {filepath}: {e}")
    
    if deleted_count > 0:
        logger.info(f"Cleaned up {deleted_count} old files from {directory}")
    
    return deleted_count
